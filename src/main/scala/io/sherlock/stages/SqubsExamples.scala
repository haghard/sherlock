package io.sherlock.stages

import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source, Zip}
import akka.stream.{ClosedShape, Graph, OverflowStrategy, SourceShape}
import org.squbs.streams.circuitbreaker.CircuitBreaker
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import scala.concurrent.duration._

object SqubsExamples {


  def timedSource(
    delay: FiniteDuration,
    interval: FiniteDuration,
    limit: Int,
    name: String,
    start: Int = 0
  ): Source[Int, akka.NotUsed] =
    Source.fromGraph(
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        // two sources
        val tickSource = Source.tick(delay, interval, ())
        val dataSource = Source.fromIterator(() ⇒ Iterator.range(start, limit))

        val sendOut = b.add(Flow[Int].map { x ⇒ x })

        // we use zip to throttle the stream
        val zip = b.add(Zip[Unit, Int]())
        val unzip = b.add(Flow[(Unit, Int)].map(_._2))

        // setup the message flow
        tickSource ~> zip.in0
        dataSource ~> zip.in1

        zip.out ~> unzip ~> sendOut
        SourceShape(sendOut.outlet)
      }
    )

  //https://squbs.readthedocs.io/en/latest/circuitbreaker/
  def scenario23(implicit sys: ActorSystem): Graph[ClosedShape, akka.NotUsed] = {
    import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

    val state = AtomicCircuitBreakerState("sample", 2, 100.milliseconds, 1.second)(sys.dispatcher, sys.scheduler)
    val settings = CircuitBreakerSettings[String, String, UUID](state)
    val circuitBreaker = CircuitBreaker(settings)

    val flow = Flow[(String, UUID)].mapAsyncUnordered(4) { elem =>
      (ref ? elem).mapTo[(String, UUID)]
    }

    Source("a" :: "b" :: "c" :: Nil)
      .map(s => (s, UUID.randomUUID))
      .via(circuitBreaker.join(flow))
      .runWith(Sink.seq)

  }


  def scenario22(implicit sys: ActorSystem): Graph[ClosedShape, akka.NotUsed] = {
    implicit val serializer = org.squbs.pattern.stream.QueueSerializer[Int]()
    //val degradingSink = new DegradingGraphiteSink[org.squbs.pattern.stream.Event[Int]]("sink_22", 2l, ms)

    val dbFlow = Flow[org.squbs.pattern.stream.Event[Int]]
      .buffer(1, OverflowStrategy.backpressure)
      .map { e ⇒
        Thread.sleep(200) //save to db
        println(s"${Thread.currentThread.getName} save ${e.entry} - ${e.index}")
        e
      }

    //https://github.com/paypal/squbs/blob/master/docs/persistent-buffer.md

    /*
      It works like the Akka Streams buffer with the difference that the content of the buffer is stored in a series of memory-mapped files
      in the directory given at construction of the PersistentBuffer. This allows the buffer size to be virtually limitless,
      not use the JVM heap for storage, and have extremely good performance in the range of a million messages/second at the same time.
   */

    //IDEA: to use PersistentBuffer as a commit log
    val file = new File("~/Projects/sherlock/disk-buffers/pqueue-22")
    val pBuffer = new org.squbs.pattern.stream.PersistentBufferAtLeastOnce[Int](file)
    val commit = pBuffer.commit[Int]
    val src = timedSource(1 second, 50 milliseconds, Int.MaxValue, "akka-source_22")

    /*val (queue, publisher) = Source
      .queue[Int](1 << 7, OverflowStrategy.backpressure)
      .toMat(Sink.asPublisher[Int](false))(Keep.both)
      .run()(mat)*/

    //read the latest saved date form DB and fetch the next page
    //queue.offer()

    //Source.fromPublisher(publisher)
    src
      .via(pBuffer.async)
      //.mapAsync(2) { e => /*ask*/ }
      .via(dbFlow.async("akka.blocking-dispatcher")) //AtLeastOnce so the writes should be idempotent
      .via(commit)
      .to(Sink.ignore)
  }

}

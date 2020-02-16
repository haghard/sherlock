package io.sherlock.stages

import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicLong, AtomicReference }

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{ RequestContext, Route }
import akka.stream.scaladsl.{ BidiFlow, Flow, GraphDSL, Keep, Sink, SinkQueueWithCancel, Source, Zip }
import akka.stream.stage.{ AsyncCallback, GraphStage, GraphStageLogic, InHandler, OutHandler, StageLogging }
import akka.stream.{ Attributes, BidiShape, ClosedShape, FlowShape, Graph, Inlet, Outlet, OverflowStrategy, SourceShape }
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.squbs.streams.circuitbreaker.CircuitBreaker
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object SqubsExamples {

  def timedSource(
    delay: FiniteDuration, interval: FiniteDuration, limit: Int, name: String, start: Int = 0): Source[Int, akka.NotUsed] =
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
      })

  //https://squbs.readthedocs.io/en/latest/circuitbreaker/
  def scenario23(implicit sys: ActorSystem) = {
    import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

    val config = ConfigFactory.parseString(
      """
        |max-failures = 2
        |call-timeout = 100 ms
        |reset-timeout = 1000 ms
        |max-reset-timeout = 2 seconds
        |exponential-backoff-factor = 2.0
      """.stripMargin)

    //maxFailures=2, callTimeout=100.millis, resetTimeout=1.second
    val state = AtomicCircuitBreakerState("x", config) //(sys.dispatcher, sys.scheduler)
    val cb = CircuitBreaker(CircuitBreakerSettings[String, String, UUID](state))

    val flow = Flow[(String, UUID)].mapAsyncUnordered(4) { elem ⇒
      Future {
        ("", UUID.randomUUID)
      }(sys.dispatcher)
      //(ref ? elem).mapTo[(String, UUID)]
    }

    Source("a" :: "b" :: "c" :: Nil)
      .map(s ⇒ (s, UUID.randomUUID))
      .via(cb.join(flow))
      .runWith(Sink.seq)(???)

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
    val src = timedSource(1.second, 50.millis, Int.MaxValue, "akka-source_22")

    /*val (queue, publisher) = Source
      .queue[Int](1 << 7, OverflowStrategy.backpressure)
      .toMat(Sink.asPublisher[Int](false))(Keep.both)
      .run()(???)*/

    //read the latest saved date form DB and fetch the next page
    //queue.offer()

    //Source.fromPublisher(publisher)
    src
      .via(pBuffer.async)
      //.mapAsync(1) { e => /*ask*/ }
      .via(dbFlow.async("akka.blocking-dispatcher")) //AtLeastOnce so the writes should be idempotent
      .via(commit)
      .to(Sink.ignore)
  }

  /*
  def bf(implicit sys: ActorSystem) = {
    val routeFlow: Flow[HttpRequest, HttpResponse, akka.NotUsed] = ??? //Route.handlerFlow(cors(corsSettings)(routes))

    BidiFlow.fromGraph(GraphDSL.create() { implicit b ⇒
      val inbound = b.add(Flow[RequestContext].map { rc ⇒
        val updated = rc.request.headers :+ RawHeader("DummyRequest", "ReqValue")
        rc.withRequest(rc.request.withHeaders(updated))
      })

      val outbound = b.add(Flow[RequestContext].map { rc ⇒
        val updated = rc.request.headers :+ RawHeader("DummyResponse", "ResValue")
        rc.withRequest(rc.request.withHeaders(updated))
      })
      BidiShape.fromFlows(inbound, outbound)
    })

    val framing: BidiFlow[ByteString, ByteString, ByteString, ByteString, akka.NotUsed] = ???
    val codec: BidiFlow[HttpRequest, ByteString, ByteString, HttpRequest, akka.NotUsed] = ???
    val action: Flow[ByteString, ByteString, akka.NotUsed] = ???

    codec.join(action)
    codec.atop(framing)

    val codec0: BidiFlow[HttpRequest, ByteString, ByteString, HttpResponse, akka.NotUsed] = ???
    val action0: Flow[ByteString, ByteString, akka.NotUsed] = ???

    codec0.join(action0)
    (codec0 atop framing).join(action0)

    reqAuthFlow(sys).join(Flow[(String, HttpRequest)].buffer(1 << 5, OverflowStrategy.backpressure))
      .via(routeFlow)
  }*/

  val ocHeader = "Unavailable"

  def reqRespFlow(sys: ActorSystem, limit: Int = 1 << 5, parallelism: Int = 4): BidiFlow[HttpRequest, (String, HttpRequest), (String, HttpRequest), HttpRequest, akka.NotUsed] = {

    def captureReq(ref: AtomicReference[Map[String, HttpRequest]])(updater: Map[String, HttpRequest] ⇒ Map[String, HttpRequest]): Unit = {
      val map = ref.get
      val updated = updater(map)
      if (ref.compareAndSet(map, updated)) () else captureReq(ref)(updater)
    }

    //https://doc.akka.io/docs/akka/current/stream/stream-graphs.html#bidirectional-flows
    BidiFlow.fromGraph(
      GraphDSL.create() { implicit b ⇒
        val reqInFlight = new AtomicReference(Map[String, HttpRequest]())

        val inbound: FlowShape[HttpRequest, (String, HttpRequest)] =
          b.add(Flow[HttpRequest].mapAsync(parallelism) { req ⇒
            val reqId = UUID.randomUUID.toString
            if (reqInFlight.get.size < limit) {
              Future {
                //import akka.pattern.ask
                //tenantsRegion.ask
                captureReq(reqInFlight)(_ + (reqId -> req))
                sys.log.info(s"flies in -> {}", reqId)
                //
                Thread.sleep(4000)
                (reqId, req)
              }(sys.dispatcher)
            } else {
              sys.log.info("reject -> {}", reqId)
              Future.successful((reqId, req.withHeaders(req.headers :+ RawHeader(ocHeader, "true"))))
            }
          })

        val outbound: FlowShape[(String, HttpRequest), HttpRequest] =
          b.add(Flow[(String, HttpRequest)].map {
            case (reqId, req) ⇒
              sys.log.info("Flies out -> {}", reqId)

              if (req.headers.find(_.name == ocHeader).isEmpty)
                captureReq(reqInFlight)(_ - reqId)

              sys.log.info(s"InFlight: [{}]", reqInFlight.get.keySet.mkString(","))
              req
          })
        BidiShape.fromFlows(inbound, outbound)
      })
  }

}

final class HttpBidiFlow[In, Out] extends GraphStage[BidiShape[In, (In, String), (Out, String), Out]] {
  //for limiting
  private val asyncCallInFlight = new AtomicLong(0l)

  //in  ~> to
  val in = Inlet[In]("in")
  val to = Outlet[(In, String)]("to")

  //out <~ from
  val from = Inlet[(Out, String)]("from")
  val out = Outlet[Out]("out")

  override val shape = BidiShape(in, to, from, out)

  override protected def initialAttributes: Attributes =
    Attributes.name("bidi-flow") //.and(ActorAttributes.dispatcher(""))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      var upstreamFinished = false
      val outBuffer = mutable.Queue[(Out, String)]()
      var callback: AsyncCallback[Try[(In, String)]] = getAsyncCallback[Try[(In, String)]](onResponse)

      def onPushFrom(elem: Out, reqId: String, isOutAvailable: Boolean): Option[(Out, String)] = {
        outBuffer.enqueue((elem, reqId))
        if (isOutAvailable) Some(outBuffer.dequeue) else None
      }

      def onResponse(res: Try[(In, String)]) = {
        res.fold({ err ⇒ ??? }, {
          case (elem, reqId) ⇒
            push(to, (elem, reqId))
        })
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          implicit val ec = materializer.executionContext
          val elem = grab(in)
          Future {
            val reqId = UUID.randomUUID.toString
            log.info("In:{}. AsyncCallInProgress:{}", reqId, asyncCallInFlight.incrementAndGet)
            Thread.sleep(4000)
            (elem, reqId)
          }.onComplete(callback.invoke)

          //inBuffer.enqueue((elem, reqId))
          // emulate downstream asking for data by calling onPull on the outlet port
          //getHandler(to).onPull()
          //push(to, (elem, reqId))
        }

        override def onUpstreamFinish(): Unit = complete(to)
        override def onUpstreamFailure(ex: Throwable): Unit = fail(to, ex)
      })

      setHandler(to, new OutHandler {
        override def onPull(): Unit = {
          /*inBuffer.dequeueFirst((_: (In, String)) ⇒ true)
            .foreach {
              case (elem, reqId) ⇒
                push(to, (elem, reqId))
            }*/
          if (!hasBeenPulled(in)) pull(in)
        }

        override def onDownstreamFinish(): Unit = completeStage()
      })

      setHandler(from, new InHandler {
        override def onPush(): Unit = {
          val (elem, reqId) = grab(from)
          log.info("Out:{}. AsyncCallInProgress:{}", reqId, asyncCallInFlight.decrementAndGet)
          outBuffer.enqueue((elem, reqId))
          // emulate downstream asking for data by calling onPull on the outlet port
          getHandler(out).onPull()

          /*onPushFrom(elem, reqId, isAvailable(out))
            .foreach {
              case (elem, reqId) ⇒
                log.info("from ~> out: {}", reqId)
                push(out, elem)
            }*/
        }

        override def onUpstreamFinish(): Unit = {
          if (outBuffer.isEmpty) completeStage()
          else upstreamFinished = true
        }

        override def onUpstreamFailure(ex: Throwable): Unit = fail(out, ex)
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (!upstreamFinished || outBuffer.nonEmpty) {
            //log.info("onPull Buffer: {}", outBuffer.size)
            outBuffer.dequeueFirst((_: (Out, String)) ⇒ true) match {
              case Some((elem, reqId)) ⇒
                //log.info("push buffered: {} size: {}", reqId, outBuffer.size)
                //log.info("buffer ~> out: {}", reqId)
                push(out, elem)
              case None ⇒
                if (!hasBeenPulled(from)) pull(from)
                else if (!hasBeenPulled(in) && isAvailable(to)) pull(in)
            }
          } else complete(out)
        }

        override def onDownstreamFinish(): Unit = cancel(from)
      })
    }
}
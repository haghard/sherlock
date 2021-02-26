package io.sherlock.stages

import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, FlowWithContext, GraphDSL, Keep, MergeHub, Sink, SinkQueueWithCancel, Source, Unzip, Zip}
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic, InHandler, OutHandler, StageLogging}
import akka.stream.{ActorAttributes, ActorMaterializer, ActorMaterializerSettings, Attributes, BidiShape, ClosedShape, FlowShape, Graph, Inlet, KillSwitches, Materializer, Outlet, OverflowStrategy, SourceShape, SystemMaterializer}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.squbs.streams.Retry
import org.squbs.streams.circuitbreaker.CircuitBreaker
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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

        val sendOut = b.add(Flow[Int].map(x ⇒ x))

        // we use zip to throttle the stream
        val zip   = b.add(Zip[Unit, Int]())
        val unzip = b.add(Flow[(Unit, Int)].map(_._2))

        // setup the message flow
        tickSource ~> zip.in0
        dataSource ~> zip.in1

        zip.out ~> unzip ~> sendOut
        SourceShape(sendOut.outlet)
      }
    )

  def scenario24(implicit sys: ActorSystem) = {

    def riskyOperation(in: String): Try[String] =
      ???

    //https://squbs.readthedocs.io/en/latest/flow-retry/
    //https://www.infoq.com/presentations/squbs/
    val retry     = Retry[String, String, UUID](max = 10)
    val riskyFlow = Flow[(String, UUID)].map { case (s, ctx) ⇒ (riskyOperation(s), ctx) }

    //SourceWithContext ???
    //https://blog.softwaremill.com/painlessly-passing-message-context-through-akka-streams-1615b11efc2c
    Source("a" :: "b" :: "c" :: Nil)
      .map(s ⇒ (s, UUID.randomUUID))
      .via(retry.join(riskyFlow))
      .runWith(Sink.foreach(println))(???)
  }

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
      """.stripMargin
    )

    //maxFailures=2, callTimeout=100.millis, resetTimeout=1.second
    val state = AtomicCircuitBreakerState("x", config) //(sys.dispatcher, sys.scheduler)
    val cb    = CircuitBreaker(CircuitBreakerSettings[String, String, UUID](state))

    val flow = Flow[(String, UUID)].mapAsyncUnordered(4) { elem ⇒
      Future {
        ("", UUID.randomUUID)
      }(sys.dispatcher)
    //(ref ? elem).mapTo[(String, UUID)]
    }

    Source("a" :: "b" :: "c" :: Nil)
      .map(s ⇒ (s, UUID.randomUUID))
      .via(cb.join(Flow[(String, UUID)].buffer(1, ???).via(flow)))
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
    val file    = new File("~/Projects/sherlock/disk-buffers/pqueue-22")
    val pBuffer = new org.squbs.pattern.stream.PersistentBufferAtLeastOnce[Int](file)
    val commit  = pBuffer.commit[Int]

    //val src0 = Source.fromIterator(() ⇒ Iterator.range(0, Int.MaxValue)).throttle(200, 1.second)

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
      .via(dbFlow.async("akka.blocking-dispatcher", 1)) //AtLeastOnce so the writes should be idempotent
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

  val buffersSize = 1 << 5

  def domain(implicit
    sys: ActorSystem
  ): FlowWithContext[HttpRequest, Promise[HttpResponse], HttpResponse, Promise[HttpResponse], Any] =
    FlowWithContext[HttpRequest, Promise[HttpResponse]]
      .withAttributes(Attributes.inputBuffer(1, 1))
      .mapAsync(4) { req: HttpRequest ⇒
        Future {
          //check headers if authorized
          null.asInstanceOf[HttpResponse]
        }(sys.dispatcher)
      }

  case class State(cnt: Long = 0L)

  def authReq(req: HttpRequest, state: State): (HttpRequest, State) = ???

  def auth(initState: State)(implicit
    sys: ActorSystem
  ): FlowWithContext[HttpRequest, Promise[HttpResponse], HttpRequest, Promise[HttpResponse], Any] = {
    val f = Flow.fromMaterializer { (mat, attr) ⇒
      //attr.attributeList.mkString(",")
      //val disp = attr.get[ActorAttributes.Dispatcher].get
      //val buf = attr.get[akka.stream.Attributes.InputBuffer].get

      FlowWithContext[HttpRequest, Promise[HttpResponse]]
        .withAttributes(Attributes.inputBuffer(1, 1))
        .mapAsync[HttpRequest](4) { req: HttpRequest ⇒
          Future {
            //set if authorized
            //req.withHeaders(req.headers :+ RawHeader("authorized", "true")
            req
          }(mat.executionContext)
        }
        .asFlow
        .scan((initState, Promise[HttpResponse](), null.asInstanceOf[HttpRequest])) { case (triple, elem) ⇒
          val req            = elem._1
          val p              = elem._2
          val state          = triple._1
          val (req0, state0) = authReq(req, state)
          (state0, p, req0)
        }
        .map { case (_: State, p: Promise[HttpResponse], req: HttpRequest) ⇒ (req, p) }
    }

    FlowWithContext.fromTuples(f)
  }

  /*
    val flow = FlowWithContext[HttpRequest, Promise[HttpResponse]]
      .withAttributes(Attributes.inputBuffer(buffersSize, buffersSize))
      .asFlow
      .scan()
      .mapAsync[(HttpResponse, Promise[HttpResponse])](4) {
        case (req: HttpRequest, p: Promise[HttpResponse]) ⇒
          Future((null.asInstanceOf[HttpResponse], p))(???)
      }
    FlowWithContext.fromTuples[HttpRequest, Promise[HttpResponse], HttpResponse, Promise[HttpResponse], Any](flow)
   */

  val (sourceQueue, switch, done) =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](buffersSize, OverflowStrategy.dropNew)
      .viaMat(KillSwitches.single)(Keep.both)
      .via(auth(State())(???))
      .via(domain(???))
      .toMat(Sink.foreach { case (response, promise) ⇒ promise.trySuccess(response) }) { case ((sink, switch), done) ⇒
        (sink, switch, done)
      }
      .addAttributes(ActorAttributes.supervisionStrategy(akka.stream.Supervision.resumingDecider))
      .run()(???)

  sourceQueue.offer(???)

  /*val (sink, switch, done) =
    MergeHub
      .source[(HttpRequest, Promise[HttpResponse])](buffersSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .via(authFlow(???))
      .toMat(Sink.foreach { case (response, promise) ⇒ promise.trySuccess(response) }) {
        case ((sink, switch), done) ⇒ (sink, switch, done)
      }
      .run()(???)
   */

  //Simular to https://github.com/hseeberger/accessus/blob/9d36dd703664565f4f1fe7b602bf4a48b0a87e8c/src/main/scala/rocks/heikoseeberger/accessus/Accessus.scala#L138
  def httpFlow(sys: ActorSystem, parallelism: Int = 4) =
    Flow.fromGraph(
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val enrichReq = b.add(Flow[HttpRequest].map(req ⇒ (req, (req, UUID.randomUUID.toString))))
        val unzip     = b.add(Unzip[HttpRequest, (HttpRequest, String)]())
        val bcastRes  = b.add(Broadcast[HttpResponse](2))
        val zip       = b.add(Zip[(HttpRequest, String), HttpResponse])
        val ask = b.add(Flow[HttpRequest].mapAsync(1) { req: HttpRequest ⇒
          Future(null.asInstanceOf[HttpResponse])(???)
        })

        //val q = b.add(Source.queue[(HttpRequest, String)](32, OverflowStrategy.dropNew))

        // format: off
      enrichReq ~> unzip.in
                   unzip.out0 ~> ask ~> bcastRes
                                        bcastRes.out(1) ~> zip.in1
                   unzip.out1 ~> zip.in0
                                 zip.out ~> Sink.foreach(???)
      // format: on
        FlowShape(enrichReq.in, bcastRes.out(0))
      }
    )

  val ocHeader = "Unavailable"

  /** https://en.wikipedia.org/wiki/Little%27s_law
    *
    * L = λ * W
    * L – the average number of items in a queuing system (queue size)
    * λ – the average number of items arriving at the system per unit of time
    * W – the average waiting time an item spends in a queuing system
    *
    * Question: How many processes running in parallel we need given
    * throughput = 100 rps and average latency = 100 millis  ?
    *
    * 100 * 0.1 = 10
    *
    * Give the numbers above, the Little’s Law shows that on average, having
    * queue size == 100,
    * parallelism factor == 10
    * average latency of single request == 100 millis
    * we can keep up with throughput = 100 rps
    *
    * Rate-limiting the number of requests that are being made
    */
  def bidiHttpFlow(
    sys: ActorSystem,
    maxInFlight: Int = 10,
    parallelism: Int = 10
  )(implicit
    m: Materializer
  ): BidiFlow[HttpRequest, (String, HttpRequest), (String, HttpRequest), HttpRequest, akka.NotUsed] = {
    val to = 1.second
    //off-heap lock-free hash tables with 64-bit keys.
    //val map = new one.nio.mem.LongObjectHashMap[Array[Byte]](maxInFlight) //maxInFlight kv only possible

    //val blobs = new one.nio.mem.OffheapBlobMap(1 << 11)

    //an abstraction for building general purpose off-heap hash tables.
    //val map = new one.nio.mem.OffheapBlobMap(10)

    def captureReq(ref: AtomicReference[Map[String, HttpRequest]])(
      updater: Map[String, HttpRequest] ⇒ Map[String, HttpRequest]
    ): Unit = {
      val map     = ref.get
      val updated = updater(map)
      if (ref.compareAndSet(map, updated)) () else captureReq(ref)(updater)
    }

    final case class RLState(deadlines: Vector[Deadline], seqNumber: Int)

    final case class RateLimiter(numOfReqs: Int, period: FiniteDuration) {
      val onePeriodAgo = Deadline.now - period
      val ref          = new AtomicReference[RLState](RLState(Vector.fill(numOfReqs)(onePeriodAgo), 0))

      @scala.annotation.tailrec
      final def eventually(f: (Deadline, RLState) ⇒ RLState): Unit = {
        val local = ref.get
        if (ref.compareAndSet(local, f(Deadline.now, local))) ()
        else eventually(f)
      }

      def hasCapacity(): Boolean = {
        val deadline = Deadline.now
        val local    = ref.get
        if (deadline - local.deadlines(local.seqNumber) < period) false
        else {
          eventually { (deadline, state) ⇒
            if (state.seqNumber + 1 == numOfReqs)
              state.copy(state.deadlines.updated(state.seqNumber, deadline), 0)
            else
              state.copy(state.deadlines.updated(state.seqNumber, deadline), state.seqNumber + 1)
          }
          true
        }
      }
    }

    //https://doc.akka.io/docs/akka/current/stream/stream-graphs.html#bidirectional-flows
    BidiFlow.fromGraph(
      GraphDSL.create() { implicit b ⇒
        // A registry of all in-flight request
        //scala.collection.concurrent.TrieMap[String, HttpRequest]()
        val reqInFlight = new AtomicReference(Map[String, HttpRequest]())
        val rateLimiter = RateLimiter(10, 1.second)

        val inbound: FlowShape[HttpRequest, (String, HttpRequest)] =
          b.add(
            Flow[HttpRequest].mapAsync(parallelism) { req ⇒
              val reqId = UUID.randomUUID.toString
              //if (reqInFlight.get.size < maxInFlight) {

              Future {

                //if(rateLimiter.hasCapacity())

                //import akka.pattern.ask
                //tenantsRegion.ask
                captureReq(reqInFlight)(_ + (reqId → req))
                sys.log.info(s"flies in -> {}", reqId)
                //
                Thread.sleep(4000)
                (reqId, req)
              }(sys.dispatcher)
            /*} else {
                sys.log.info("reject -> {}", reqId)
                Future.successful((reqId, req.withHeaders(req.headers :+ RawHeader(ocHeader, "true"))))
              }*/

            //req.entity.dataBytes
            /*if (reqInFlight.get.size < maxInFlight)
                req.entity.toStrict(to)
                  .map { bytes ⇒
                    val r = blobs.put(reqId.getBytes, bytes.data.toArray) //.toByteBuffer.array

                    (reqId, req)
                  }(sys.dispatcher)*/

            /*Future {
                  //import akka.pattern.ask
                  //tenantsRegion.ask
                  captureReq(reqInFlight)(_ + (reqId → req))

                  req.entity.toStrict(1.second)

                  map.put(reqId.getBytes, )

                  sys.log.info(s"flies in -> {}", reqId)
                  //
                  Thread.sleep(4000)
                  (reqId, req)
                }(sys.dispatcher)*/
            }
          )

        val outbound: FlowShape[(String, HttpRequest), HttpRequest] =
          b.add(
            Flow[(String, HttpRequest)].map { case (reqId, req) ⇒
              sys.log.info("Flies out -> {}", reqId)

              if (req.headers.find(_.name == ocHeader).isEmpty)
                captureReq(reqInFlight)(_ - reqId)

              sys.log.info(s"InFlight: [{}]", reqInFlight.get.keySet.mkString(","))
              req
            }
          )
        BidiShape.fromFlows(inbound, outbound)
      }
    )
  }

}

//https://github.com/calvinlfer/Akka-Streams-custom-stream-processing-examples
final class HttpBidiFlow[In, Out] extends GraphStage[BidiShape[In, (In, String), (Out, String), Out]] {
  //for limiting
  private val asyncCallInFlight = new AtomicLong(0L)

  //in ~> to
  val in = Inlet[In]("in")
  val to = Outlet[(In, String)]("to")

  //out <~ from
  val from = Inlet[(Out, String)]("from")
  val out  = Outlet[Out]("out")

  override val shape = BidiShape(in, to, from, out)

  override protected def initialAttributes: Attributes =
    Attributes.name("bidi-flow") //.and(ActorAttributes.dispatcher(""))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      var upstreamFinished                           = false
      val outBuffer                                  = mutable.Queue[(Out, String)]()
      var callback: AsyncCallback[Try[(In, String)]] = _

      override def preStart(): Unit =
        callback = getAsyncCallback[Try[(In, String)]](resAvailable)

      def onPushFrom(elem: Out, reqId: String, isOutAvailable: Boolean): Option[(Out, String)] = {
        outBuffer.enqueue((elem, reqId))
        if (isOutAvailable) Some(outBuffer.dequeue) else None
      }

      def resAvailable(res: Try[(In, String)]) =
        res.fold(
          err ⇒ ???,
          { case (elem, reqId) ⇒
            push(to, (elem, reqId))
          }
        )

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            implicit val ec = materializer.executionContext
            val elem        = grab(in)
            //ask
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

          override def onUpstreamFinish(): Unit               = complete(to)
          override def onUpstreamFailure(ex: Throwable): Unit = fail(to, ex)
        }
      )

      setHandler(
        to,
        new OutHandler {
          override def onPull(): Unit =
            /*inBuffer.dequeueFirst((_: (In, String)) ⇒ true)
            .foreach {
              case (elem, reqId) ⇒
                push(to, (elem, reqId))
            }*/
            if (!hasBeenPulled(in)) pull(in)

          //override def onDownstreamFinish(cause: Throwable): Unit = completeStage()
        }
      )

      setHandler(
        from,
        new InHandler {
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

          override def onUpstreamFinish(): Unit =
            if (outBuffer.isEmpty) completeStage()
            else upstreamFinished = true

          override def onUpstreamFailure(ex: Throwable): Unit = fail(out, ex)
        }
      )

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            // we query here because we artificially calls onPull
            // and we must not violate the GraphStages guarantees
            if (!upstreamFinished || outBuffer.nonEmpty) {
              if (isAvailable(out))
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

          override def onDownstreamFinish(cause: Throwable): Unit = cancel(from)
        }
      )
    }
}

package io.sherlock.http

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash, Terminated }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.headers.`Last-Event-ID`
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{ BroadcastHub, Keep, RunnableGraph, Source }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, OverflowStrategy }
import akka.util.Timeout
import brave.Tracing
import io.sherlock.core.{ HeartBeat, HeartBeatTrace, Service, ServiceRegistry }
import io.sherlock.stages.ActorBasedSource
import io.sherlock.stages.ActorBasedSource.{ AssignStageActor, FailStage }

import scala.concurrent.duration._

class HttpApi(serviceName: String, registry: ActorRef, tracing: Tracing)(implicit system: ActorSystem) extends Serialization {
  val httpApiTracer = tracing.tracer
  //to give a chance to read from a local store
  implicit val timeout: Timeout = 3.seconds

  val route: Route =
    /*path("greeting" / Segment) { name ⇒
    complete("Hello " + name)
  }*/

    pathPrefix("service") {
      path(Segments) { root ⇒
        get {
          complete {
            (registry ? ServiceRegistry.Get("/" + root.mkString("/"))).mapTo[Service.Result]
          }
        }
      } ~ pathEnd {
        post {
          entity(as[HeartBeat]) { heartbeat ⇒
            complete {
              val rootSpan = httpApiTracer.newTrace().name("hb").start()
              system.log.info("POST {}", heartbeat)
              registry ! HeartBeatTrace(heartbeat, rootSpan.context, httpApiTracer)
              //heartbeat
              HttpResponse(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  sealed trait Protocol {
    def id: Int
  }

  case class ProtocolA(id: Int) extends Protocol

  case class ProtocolB(id: Int) extends Protocol

  val pubSub = system.actorOf(Props(new Actor with Stash with ActorLogging {
    def receive: Receive = {
      case _: Int ⇒ stash()
      case AssignStageActor(sseChannel: ActorRef) ⇒
        unstashAll()
        //DistributedPubSub() .subscribe (self)
        context.watch(sseChannel)
        context.become(receiveNew(sseChannel))
    }

    def receiveNew(sseChannel: ActorRef): Receive = {
      case Terminated(`sseChannel`) ⇒
        log.info("{} terminated ", sseChannel)
        context.stop(self)
      case msg: Int ⇒ // message from pub sub
        log.info("publish")
        sseChannel ! ProtocolA(msg)
    }
  }))

  lazy val liveSseStream = {
    //Source.tick(4.seconds, 4.seconds, 1)
    system.scheduler.schedule(1.second, 300.millis)(pubSub ! 1)(system.dispatcher)

    //one-to-many
    implicit val m = ActorMaterializer(ActorMaterializerSettings(system).withInputBuffer(1, 1))
    val source = Source.fromGraph(new ActorBasedSource[Protocol](pubSub, 1 << 5, FailStage))
      .scan(0)((a, _) ⇒ a + 1)
      .map(timeToServerSentEvent(LocalTime.now, _))
      .keepAlive(4.seconds / 2, () ⇒ ServerSentEvent.heartbeat)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right).run
    source
    //source.runWith(Sink.ignore)
    //source.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right).run()
  }

  /*def initIngestionHub[M](sink: Sink[DroneData, M]): M = {
    val killSwitch = KillSwitches.shared("ingestion-hub")
    MergeHub.source[DroneData](perProducerBufferSize = 32)
      .via(killSwitch.flow)
      .toMat(sink)(Keep.both)
      .run()
  }*/

  //The main disadvantage here being that you cannot catch an type cast error
  lazy val liveSseStream2 = {
    implicit val m = ActorMaterializer(ActorMaterializerSettings(system).withInputBuffer(1, 1))
    system.scheduler.schedule(1.second, 500.millis)(pubSub ! 1)(system.dispatcher)
    implicit val d = system.dispatcher
    val s = Source.actorRef[Int](1 << 8, OverflowStrategy.dropHead)
      .scan(0)((a, _) ⇒ a + 1)
      .map(timeToServerSentEvent(LocalTime.now, _))
      .keepAlive(4.seconds / 2, () ⇒ ServerSentEvent.heartbeat)
      .mapMaterializedValue { actor ⇒ pubSub ! AssignStageActor(actor) }
    /*.watchTermination() { (_, termination) ⇒
        termination.foreach { _ ⇒
          println("************** sse-channel terminated")
          akka.NotUsed
        }
      }*/

    val runnableGraph: RunnableGraph[Source[ServerSentEvent, akka.NotUsed]] =
      s.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

    runnableGraph.run()
    //s
  }

  def liveSse(): Route =
    path("live")(get(complete(liveSseStream)))

  def liveSse2(): Route = {
    path("live2") {
      get {
        complete {
          liveSseStream2
        }
      }
    }
  }

  def sse(size: Int, duration: FiniteDuration = 4.seconds): Route = {
    path("sse") {
      get {
        optionalHeaderValueByName(`Last-Event-ID`.name) { lastEventId ⇒
          val fromSeqNum = lastEventId.map(_.trim.toInt).getOrElse(0) + 1
          complete {
            Source.tick(duration, duration, fromSeqNum)
              .scan(fromSeqNum)((a, _) ⇒ a + 1)
              .map(timeToServerSentEvent(LocalTime.now, _))
              .keepAlive(duration / 2, () ⇒ ServerSentEvent.heartbeat)

          }
        }
      }
    }
  }

  def timeToServerSentEvent(time: LocalTime, id: Int) =
    ServerSentEvent(id.toString + ": " + DateTimeFormatter.ISO_LOCAL_TIME.format(time))
}
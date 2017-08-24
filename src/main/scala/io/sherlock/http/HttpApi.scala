package io.sherlock.http

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import brave.Tracing
import io.sherlock.core.{HeartBeat, HeartBeatTrace, Service, ServiceRegistry}

import scala.concurrent.duration._
import scala.util.Try

class HttpApi(serviceName: String, registry: ActorRef, tracing: Tracing)(system: ActorSystem) extends Serialization {
  val httpApiTracer = tracing.tracer
  //to give a chance to read from a local store
  implicit val timeout: Timeout = 3.seconds

  val sseRoute: Route = sse(20)

  val route: Route =
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
    } ~ sseRoute

  import akka.http.scaladsl.model.MediaTypes.`text/event-stream`
  import akka.http.scaladsl.model.StatusCodes.BadRequest
  import akka.http.scaladsl.model.headers.`Last-Event-ID`
  import akka.http.scaladsl.model.sse.ServerSentEvent
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  //Route.handlerFlow(Flow.fromSinkAndSource(Sink.seq[HttpResponse], Source.empty[HttpRequest])

  def sse(size: Int, duration: FiniteDuration = 4.seconds): Route = {
    get {
      optionalHeaderValueByName(`Last-Event-ID`.name) { lastEventId ⇒
        val src = Try(lastEventId.map(_.trim.toInt).getOrElse(0) + 1).fold({ ex =>
          Source.single(ServerSentEvent("Integral number expected for Last-Event-ID header!"))
        }, { fromSeqNum =>
            Source.tick(duration, duration, fromSeqNum)
              .scan(fromSeqNum)((a, _) ⇒ a + 1)
              .map(timeToServerSentEvent(LocalTime.now, _))
              .keepAlive(duration / 2, () ⇒ ServerSentEvent.heartbeat)
        })
        complete(src)
      }
    }
  }

  def timeToServerSentEvent(time: LocalTime, id: Int) =
    ServerSentEvent(id.toString + ": " + DateTimeFormatter.ISO_LOCAL_TIME.format(time))
}
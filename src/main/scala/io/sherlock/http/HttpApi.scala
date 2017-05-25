package io.sherlock.http

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import brave.Tracing
import io.sherlock.core.{ HeartBeat, HeartBeatTrace, Service, ServiceRegistry }

import scala.concurrent.duration._

class HttpApi(serviceName: String, registry: ActorRef, tracing: Tracing)(system: ActorSystem) extends Serialization {
  val httpApiTracer = tracing.tracer
  //to give a chance to read from a local store
  implicit val timeout: Timeout = 3.seconds

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
    }
}
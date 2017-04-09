package io.sherlock.http

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import io.sherlock.core.{ HeartBeat, Service, ServiceRegistry }

import scala.concurrent.duration._

class Routes(services: ActorRef) extends Serialization {

  val route: Route =
    pathPrefix("service") {
      path(Segments) { root ⇒
        get {
          implicit val timeout: Timeout = 5.seconds
          //to give a chance to read from a local store
          complete {
            (services ? ServiceRegistry.Get("/" + root.mkString("/"))).mapTo[Service.Result]
          }
        }
      } ~ (pathEnd & post) {
        entity(as[HeartBeat]) { heartbeat ⇒
          services ! heartbeat
          complete(StatusCodes.NoContent)
        }
      }
    }
}

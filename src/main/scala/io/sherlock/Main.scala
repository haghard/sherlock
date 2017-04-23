package io.sherlock

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.cluster.http.management.ClusterHttpManagement
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.sherlock.core.ServiceRegistry
import io.sherlock.http.Routes
import net.ceedubs.ficus.Ficus._

object Main extends App {
  val conf = ConfigFactory.load()
  implicit val system = ActorSystem("sd", conf)
  implicit val materializer = ActorMaterializer.create(system)
  import system.dispatcher

  val seedNodes = conf.as[List[String]]("akka.cluster.seed-nodes")
  val akkaPort = conf.as[Int]("akka.remote.netty.tcp.port")
  val hostname = conf.as[String]("akka.remote.netty.tcp.hostname")
  val httpPort = conf.as[Int]("port")
  val services = system.actorOf(ServiceRegistry.props, "service-registry")

  val httpApi = new Routes(services).route

  //ClusterHttpManagement(cluster)
  //CoordinatedShutdown(system)

  Http()
    .bindAndHandle(httpApi, hostname, httpPort)
    .onComplete {
      case scala.util.Success(_) ⇒
        println(s"seed-nodes: ${seedNodes.mkString(",")}")
        println(s"akka node: ${hostname}:${akkaPort}")
        println(s"http port: ${httpPort}")
      case scala.util.Failure(_) ⇒
        System.exit(-1)
    }
}
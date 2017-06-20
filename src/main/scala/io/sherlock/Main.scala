package io.sherlock

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import brave.Tracing
import io.sherlock.core.UniqueHostsStage
import zipkin.reporter.AsyncReporter
import zipkin.reporter.okhttp3.OkHttpSender

import scala.concurrent.Await
//import akka.cluster.http.management.ClusterHttpManagement
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.sherlock.core.ServiceRegistry
import io.sherlock.http.HttpApi
import net.ceedubs.ficus.Ficus._
import scala.concurrent.duration._

object Main extends App {
  val version = "v0.0.1.RELEASE"
  val conf = ConfigFactory.load()
  implicit val system = ActorSystem("sd", conf)
  implicit val materializer = ActorMaterializer.create(system)
  import system.dispatcher

  val akkaPort = conf.as[Int]("akka.remote.netty.tcp.port")
  val hostname = conf.as[String]("akka.remote.netty.tcp.hostname")
  val name = s"${hostname}:${akkaPort}"
  val seedNodes = conf.as[List[String]]("akka.cluster.seed-nodes")
  val httpPort = conf.as[Int]("port")
  val registry = system.actorOf(ServiceRegistry.props, "service-registry")

  //ClusterHttpManagement(cluster)
  //CoordinatedShutdown(system)

  val sender = OkHttpSender.create(conf.as[String]("zipkin-url"))
  val reporter: AsyncReporter[zipkin.Span] = AsyncReporter.builder(sender).build()
  val tracing = Tracing.newBuilder().localServiceName(name)
    .reporter(reporter).build()

  val httpApi = new HttpApi(name, registry, tracing)(system).route

  val routeFlow = Route.handlerFlow(httpApi)
  val hosts = new AtomicReference(Set[String]())
  val uniqueHosts = new UniqueHostsStage(hosts)
  val httpGraph = (Flow.fromGraph(uniqueHosts) via routeFlow)

  Http()
    .bindAndHandle(httpGraph, hostname, httpPort)
    .onComplete {
      case scala.util.Success(_) ⇒
        println(s"seed-nodes: ${seedNodes.mkString(",")}")
        println(s"akka node: ${hostname}:${akkaPort}")
        println(s"http port: ${httpPort}")
        println(Console.GREEN +
          """
              ___  ____   ___  __   __  ___   ___     ______
             / __| | __| | _ \ \ \ / / | __| | _ \    \ \ \ \
             \__ \ | _|  |   /  \ V /  | _|  |   /     ) ) ) )
             |___/ |___| |_|_\   \_/   |___| |_|_\    /_/_/_/
             ========================================
        """ + "\n" + s""":: ($version) ::""" + Console.RESET)

      case scala.util.Failure(_) ⇒
        Await.result(system.terminate(), 10.seconds)
        System.exit(-1)
    }

  sys.addShutdownHook {
    tracing.close
    reporter.close
    sender.close
    Await.result(system.terminate(), 10.seconds)
  }
}
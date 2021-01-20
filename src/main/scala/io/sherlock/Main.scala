package io.sherlock

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import brave.Tracing
import io.sherlock.core.{ActorCache, UniqueHostsStage}
import zipkin.reporter.AsyncReporter
import zipkin.reporter.okhttp3.OkHttpSender

import scala.concurrent.{Await, Future}
import akka.http.scaladsl.Http
import akka.stream.contrib.PassThroughFlow
import akka.stream.{ActorMaterializer, KillSwitches, Materializer, OverflowStrategy, SystemMaterializer}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import io.sherlock.core.ServiceRegistry
import io.sherlock.http.HttpApi
import io.sherlock.stages.{CacheStage, HttpBidiFlow, SqubsExamples}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._

object Main extends App with OptsSupport {
  val version = "v0.0.1.RELEASE"

  println("****** " + args.toList.mkString(","))

  val opts = argsToOpts(args.toList)
  applySystemProperties(opts)

  val conf                  = ConfigFactory.load()
  implicit val system       = ActorSystem("sd", conf)
  implicit val materializer = SystemMaterializer(system).materializer
  //Materializer.matFromSystem(system)

  import system.dispatcher

  val akkaPort  = conf.as[Int]("akka.remote.netty.tcp.port")
  val hostname  = conf.as[String]("akka.remote.netty.tcp.hostname")
  val name      = s"${hostname}:${akkaPort}"
  val seedNodes = conf.as[List[String]]("akka.cluster.seed-nodes")
  val httpPort  = conf.as[Int]("port")
  val registry  = system.actorOf(ServiceRegistry.props, "service-registry")

  //ClusterHttpManagement(cluster)
  //CoordinatedShutdown(system)

  val sender                               = OkHttpSender.create(conf.as[String]("zipkin-url"))
  val reporter: AsyncReporter[zipkin.Span] = AsyncReporter.builder(sender).build()
  val tracing = Tracing
    .newBuilder()
    .localServiceName(name)
    .reporter(reporter)
    .build()

  val httpApi = new HttpApi(name, registry, tracing)(system).route

  val routeFlow   = Route.toFlow(httpApi)
  val hosts       = new AtomicReference(Set[String]())
  val uniqueHosts = new UniqueHostsStage(hosts)

  //curl http://127.0.0.1:9090/ping/haghard

  BidiFlow
    .fromGraph(new HttpBidiFlow[HttpRequest, HttpRequest])
    .join(Flow[(HttpRequest, String)].mapAsyncUnordered(4) { case (r, uuid) ⇒
      Future {

        (r, uuid)
      }
    })

  //val httpGraph =
  BidiFlow
    .fromGraph(new HttpBidiFlow[HttpRequest, HttpRequest])
    .join(Flow.fromFunction[(HttpRequest, String), (HttpRequest, String)](identity))
    //.join(Flow[(HttpRequest, String)].buffer(1 << 2, OverflowStrategy.backpressure)).map(_._1)
    .via(routeFlow)

  //val httpGraph =
  SqubsExamples
    .bidiHttpFlow(system, maxInFlight = 1)
    .join(Flow.fromFunction[(String, HttpRequest), (String, HttpRequest)](identity))
    //.join(Flow[(String, HttpRequest)].buffer(1 << 2, OverflowStrategy.backpressure))
    .via(routeFlow)

  val preRequest: Flow[HttpRequest, HttpRequest, akka.NotUsed] =
    Flow[HttpRequest].mapAsync(1) { r ⇒
      Future {
        system.log.info("preRequest")
        //req auth headers
        r
      }(materializer.executionContext)
    }

  val postRequest: Flow[(HttpRequest, HttpResponse), HttpResponse, akka.NotUsed] =
    Flow[(HttpRequest, HttpResponse)].map { case (req, resp) ⇒
      system.log.info("postRequest")
      resp
    }

  val httpGraph: Flow[HttpRequest, HttpResponse, akka.NotUsed] =
    preRequest
      //.via(routeFlow)
      .via(akka.stream.contrib.PassThroughFlow(routeFlow))
      .via(postRequest)

  //akka.stream.contrib.PassThroughFlow(routeFlow)

  /*Source
    .queue[HttpRequest](10, OverflowStrategy.backpressure)
    .via(httpGraph)
    .viaMat(KillSwitches.single)(Keep.both)
    .toMat(Sink.foreach { case response ⇒ ??? }) {
      case ((sink, switch), done) => (sink, switch, done)
    }*/
  //.to(Sink.foreach { resp => ??? })

  /*implicit val t = akka.util.Timeout(1.seconds)
  val cache = system.actorOf(ActorCache.props)
  val stage = new CacheStage(cache)(t)*/

  //val httpGraph = (Flow.fromGraph(check(cache)) via routeFlow)
  //val httpGraph = (Flow.fromGraph(uniqueHosts) via routeFlow)
  //val httpGraph = Flow.fromGraph(stage) via routeFlow

  //val httpGraph = (Flow.fromGraph(new BloomFilterStage()) via routeFlow)

  /*def check(src: ActorRef)(implicit t: akka.util.Timeout) = {
    import akka.pattern.ask
    Flow[HttpRequest].mapAsync(4)(req ⇒ (src ? req).mapTo[HttpRequest])
  }*/

  /*new SwaggerApi().route*/

  /*val trustfulCtx: SSLContext = {
    val password: Array[Char] = "qwerty".toCharArray
    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    //KeyStore.getInstance("JKS")
    val keystore: InputStream = new FileInputStream("./fsa.jks")
    //val keystore: InputStream = new FileInputStream("./fsa.jks")
    //getClass.getClassLoader.getResourceAsStream("server.p12")

    require(keystore != null, "Keystore required !!!")
    ks.load(keystore, password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    sslContext
  }*/

  Http()
    .newServerAt(hostname, httpPort)
    .bindFlow(httpGraph)
    /*.bindAndHandle(
      handler = httpGraph /*routeFlow*/,
      interface = hostname,
      port = httpPort
      /*, connectionContext = ConnectionContext.https(trustfulCtx)*/
    )*/
    .onComplete {
      case scala.util.Success(_) ⇒
        println(s"seed-nodes: ${seedNodes.mkString(",")}")
        println(s"akka node: ${hostname}:${akkaPort}")
        println(s"https port: ${httpPort}")
        println(
          Console.GREEN +
          """
              ___  ____   ___  __   __  ___   ___     ______
             / __| | __| | _ \ \ \ / / | __| | _ \    \ \ \ \
             \__ \ | _|  |   /  \ V /  | _|  |   /     ) ) ) )
             |___/ |___| |_|_\   \_/   |___| |_|_\    /_/_/_/
             ========================================
        """ + "\n" + s""":: ($version) ::""" + Console.RESET
        )

      case scala.util.Failure(_) ⇒
        Await.result(system.terminate, 10.seconds)
        System.exit(-1)
    }

  sys.addShutdownHook {
    tracing.close
    reporter.close
    sender.close
    Await.result(system.terminate, 10.seconds)
  }
}

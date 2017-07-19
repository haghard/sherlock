import sbt._

object Dependencies {
  val akkaVersion = "2.5.3"

  object Compile {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaDistData = "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val logback = "ch.qos.logback"  %   "logback-classic" % "1.1.2"

    val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.9"
    val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.9"
    val ficus = "com.iheart" %% "ficus" % "1.4.0"
    val akkaStreams = "com.typesafe.akka" %% "akka-stream"  % akkaVersion


    val opentracing = "io.opentracing" % "opentracing-api" % "0.21.0"
    val zipkinSender = "io.zipkin.reporter" % "zipkin-sender-okhttp3" % "0.7.0"
    val brave = "io.zipkin.brave" % "brave" % "4.2.0"

    //val guava = "com.google.guava" % "guava" % "21.0"
    //val zipkinClient = "com.beachape" %% "zipkin-futures" % "0.2.1"
    //val tracing = "com.github.levkhomich" %% "akka-tracing-http"  % "0.6.1-SNAPSHOT"
    //val zipkin = "io.zipkin.finagle" %% "zipkin-finagle-http" % "0.4.0"
    //val akkaClusterManagement = "com.lightbend.akka" %% "akka-management-cluster-http" %  "0.2+20170418-2254"
    //val zipkin2 = "com.beachape" %% "zipkin-futures" % "0.2.1"

    val jvmUtil = "com.twitter"   %%  "util-jvm"   %   "6.45.0"

    val algebird = "com.twitter" %% "algebird-core" % "0.13.0"

    val all = Seq(akkaActor, akkaHttp, akkaHttp, akkaSprayJson, akkaDistData, slf4j, logback,
      ficus, akkaStreams, algebird, jvmUtil) ++ Seq(opentracing, zipkinSender, brave) /*akkaClusterManagement,*/

  }

  object Test {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    val all = Seq(akkaTestkit, scalatest)
  }
}

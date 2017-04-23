import sbt._

object Dependencies {
  val akkaVersion = "2.5.0"

  object Compile {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaDistData = "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val logback = "ch.qos.logback"  %   "logback-classic" % "1.1.2"

    val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.5"
    val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5"
    val ficus = "com.iheart" %% "ficus" % "1.4.0"
    val akkaClusterManagement = "com.lightbend.akka" %% "akka-management-cluster-http" %  "0.2+20170418-2254"
    val akkaStreams = "com.typesafe.akka" %% "akka-stream"  % akkaVersion

    //val guava = "com.google.guava" % "guava" % "21.0"
    val hasher = "com.roundeights" %% "hasher" % "1.2.0"

    val all = Seq(akkaActor, akkaHttp, akkaHttp, akkaSprayJson,
      akkaDistData, akkaClusterManagement, slf4j, logback, ficus, akkaStreams, hasher)/*, metrics*/
  }

  object Test {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    val all = Seq(akkaTestkit, scalatest)
  }
}

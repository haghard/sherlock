import sbt._

object Dependencies {
  val akkaVersion = "2.4.17"

  object Compile {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaDistData = "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val logback = "ch.qos.logback"  %   "logback-classic" % "1.1.2"

    val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.5"
    val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5"
    val ficus = "com.iheart" %% "ficus" % "1.4.0"
    val all = Seq(akkaActor, akkaHttp, akkaHttp, akkaSprayJson, akkaDistData, slf4j, logback, ficus)/*, metrics*/
  }

  object Test {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    val all = Seq(akkaTestkit, scalatest)
  }
}

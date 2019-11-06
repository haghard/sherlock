import sbt._

object Dependencies {
  val akkaVersion = "2.5.21"
  val squbsVersion = "0.9.1"

  object Compile {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaDistData = "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

    val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.9"
    val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.9"
    val ficus = "com.iheart" %% "ficus" % "1.4.0"
    val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaVersion

    val opentracing = "io.opentracing" % "opentracing-api" % "0.21.0"
    val zipkinSender = "io.zipkin.reporter" % "zipkin-sender-okhttp3" % "0.7.0"
    val brave = "io.zipkin.brave" % "brave" % "4.2.0"

    //val guava = "com.google.guava" % "guava" % "21.0"
    //val zipkinClient = "com.beachape" %% "zipkin-futures" % "0.2.1"
    //val tracing = "com.github.levkhomich" %% "akka-tracing-http"  % "0.6.1-SNAPSHOT"
    //val zipkin = "io.zipkin.finagle" %% "zipkin-finagle-http" % "0.4.0"
    //val akkaClusterManagement = "com.lightbend.akka" %% "akka-management-cluster-http" %  "0.2+20170418-2254"
    val akkaClusterManagement = "com.lightbend.akka" %% "akka-management-cluster-http" % "0.3"
    //val zipkin2 = "com.beachape" %% "zipkin-futures" % "0.2.1"

    val jvmUtil = "com.twitter" %% "util-jvm" % "6.45.0"

    val algebird = "com.twitter" %% "algebird-core" % "0.13.0"

    /*
      Cassandra Version 1.1.1 uses SnapTree (https://github.com/nbronson/snaptree) for MemTable representation,
      which claims to be "A drop-in replacement for ConcurrentSkipListMap, with the additional guarantee that clone()
      is atomic and iteration has snapshot isolation
    */
    val snaptree = "edu.stanford.ppl" % "snaptree" % "0.1" //0.2

    /*
    FingerTree is an immutable sequence data structure in Scala programming language, offering O(1) prepend and
    append, as well as a range of other useful properties [^1]. Finger trees can be used as building blocks for
    queues, double-ended queues, priority queues, indexed and summed sequences.
    */
    val fingertree = "de.sciss" %% "fingertree" % "1.5.4"

    //https://medium.com/@AlirezaMeskin/implementing-immutable-trie-in-scala-c0ab58fd401
    val radixtree = "com.rklaehn" %% "radixtree" % "0.5.1"

    val isarn = "org.isarnproject" %% "isarn-collections" % "0.0.4"

    //2.11
    //val squbs    = "org.squbs" %% "squbs-pattern" % squbsVersion
    //val chronicle = "net.openhft" % "chronicle-queue" % "4.5.13"

    //val swakka = "net.jtownson" %% "swakka" % "0.1a-SNAPSHOT"

    //BinaryHeap which is an implementation of a Priority Queue
    //https://efekahraman.github.io/2019/03/an-example-of-free-monads-and-optimization
    val catsColl = "org.typelevel" %% "cats-collections-core" % "0.7.0"

    val btree = "xyz.hyperreal" %% "b-tree" % "0.5"

    // li haoyi ammonite repl embed
    val ammonite = ("com.lihaoyi" % "ammonite" % "1.6.9" % "test").cross(CrossVersion.full)

    val all = Seq(akkaActor, akkaHttp, akkaHttp, akkaSprayJson, akkaDistData, slf4j, logback, snaptree,
      fingertree, radixtree, isarn, ammonite, catsColl, btree, /*swakka,*/
      ficus, akkaStreams, algebird, jvmUtil) ++ Seq(opentracing, zipkinSender, brave /*, akkaClusterManagement*/ )
  }

  object Test {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    val all = Seq(akkaTestkit, scalatest)
  }
}

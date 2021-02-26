import sbt._

object Dependencies {
  val akkaVersion = "2.6.13"
  val squbsVersion = "0.14.0"

  object Compile {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaDistData = "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

    val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.2.4"
    val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.4"

    val ficus = "com.iheart" %% "ficus" % "1.4.0"
    val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaStreamContrib = ("com.typesafe.akka" %% "akka-stream-contrib" % "0.11") //.excludeAll("com.typesafe.akka")

    val opentracing = "io.opentracing" % "opentracing-api" % "0.21.0"
    val zipkinSender = "io.zipkin.reporter" % "zipkin-sender-okhttp3" % "0.7.0"
    val brave = "io.zipkin.brave" % "brave" % "4.2.0"

    //val guava = "com.google.guava" % "guava" % "21.0"
    //val zipkinClient = "com.beachape" %% "zipkin-futures" % "0.2.1"
    //val tracing = "com.github.levkhomich" %% "akka-tracing-http"  % "0.6.1-SNAPSHOT"
    //val zipkin = "io.zipkin.finagle" %% "zipkin-finagle-http" % "0.4.0"
    //val akkaClusterManagement = "com.lightbend.akka" %% "akka-management-cluster-http" %  "0.2+20170418-2254"

    val akkaClusterManagement = "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.0.9"
    //val zipkin2 = "com.beachape" %% "zipkin-futures" % "0.2.1"

    val jvmUtil = "com.twitter" %% "util-jvm" % "6.45.0"

    val algebird = "com.twitter" %% "algebird-core" % "0.13.0"

    val cd = "com.datastax.cassandra" % "cassandra-driver-extras" % "3.10.2"
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
    val sketches = "org.isarnproject" %% "isarn-sketches" % "0.3.0"

    //val swakka = "net.jtownson" %% "swakka" % "0.1a-SNAPSHOT"

    //BinaryHeap which is an implementation of a Priority Queue
    //https://github.com/typelevel/cats-collections/blob/master/bench/src/main/scala/HeapBench.scala
    //https://efekahraman.github.io/2019/03/an-example-of-free-monads-and-optimization
    //https://github.com/politrons/reactiveScala/blob/master/scala_features/src/main/scala/app/impl/algorithms/TreeDS.scala
    val catsColl = "org.typelevel" %% "cats-collections-core" % "0.7.0"

    val btree = "xyz.hyperreal" %% "b-tree" % "0.5"

    val playJson = "com.typesafe.play" %% "play-json" % "2.7.4"
    val jsoniter = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.0.1"
    
    val ammonite =
      ("com.lihaoyi" % "ammonite" % "2.3.8-32-64308dc3" % "test").cross(CrossVersion.full)
      //("com.lihaoyi" % "ammonite" % "2.2.0" % "test").cross(CrossVersion.full)

    //https://squbs.readthedocs.io/en/latest/circuitbreaker/
    val squbsP =    "org.squbs" %% "squbs-pattern" % squbsVersion  //.excludeAll("com.typesafe.akka")
    val squbsExt =  "org.squbs" %% "squbs-ext"     % squbsVersion  //.excludeAll("com.typesafe.akka")

    val chronicle = "net.openhft" % "chronicle-queue" % "4.5.13"

    val oneNio = "ru.odnoklassniki" % "one-nio" % "1.2.0"

    val all = Seq(akkaActor, akkaHttp, akkaHttp, akkaSprayJson, akkaDistData, slf4j, logback, snaptree,
      fingertree, radixtree, isarn, ammonite, catsColl, btree, cd, /*swakka,*/
      ficus, akkaStreams, algebird, jvmUtil) ++ Seq(opentracing, zipkinSender, brave /*, akkaClusterManagement*/ , playJson, jsoniter,
      squbsP, squbsExt, akkaStreamContrib, oneNio)

  }

  object Test {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    val all = Seq(akkaTestkit, scalatest)
  }
}

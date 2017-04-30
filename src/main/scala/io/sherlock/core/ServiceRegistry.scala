package io.sherlock.core

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{ DistributedData, ORSet, ORSetKey }
import brave.propagation.TraceContext
import io.sherlock.Main.conf

import scala.concurrent.duration._

object ServiceRegistry {
  case class Get(contextRoot: String)

  def rootToName(root: String): String = root.replaceAll("/", "_")

  def props = Props(new ServiceRegistry())
}

class ServiceRegistry extends Actor with ActorLogging {
  import ServiceRegistry._
  import net.ceedubs.ficus.Ficus._

  implicit val timeout = conf.as[FiniteDuration]("ts")
  implicit val node = Cluster(context.system)

  val DataKey = ORSetKey[String](self.path.name)

  val replicator = DistributedData(context.system).replicator
  replicator ! Subscribe(DataKey, self)

  override def preStart(): Unit =
    log.info("Start: {}", self.path.name)

  def getOrCreate(serviceName: String): ActorRef =
    context.child(serviceName).getOrElse(context.actorOf(Service.props, serviceName))

  def getOrCreateAndSubscribe(serviceName: String): ActorRef =
    context.child(serviceName).getOrElse {
      log.info("A new service:  {}", serviceName)
      replicator ! Update(DataKey, ORSet(), WriteLocal)(_ + serviceName)
      context.actorOf(Service.props, serviceName)
    }

  override def receive = {
    case m @ HeartBeatTrace(hb, cxt, tracer) ⇒
      //HeartBeat(_, path, _) ⇒
      val registrySpan = tracer.newChild(cxt).name("registry").start()
      val serviceName = rootToName(hb.path)
      log.info(s"hb {} for {}", hb, serviceName)
      getOrCreateAndSubscribe(serviceName) ! m
      registrySpan.finish()
    case Get(path) ⇒
      val rootName = rootToName(path)
      log.info("Get root name:{}", rootName)
      context.child(rootName) match {
        case None ⇒
          sender() ! Service.Result(Map.empty)
        case Some(instance) ⇒
          instance forward Service.GetAccuracy
      }
    case change @ Changed(DataKey) ⇒
      val data = change.get(DataKey)
      log.info(s"[Registry-Replication] by key:{} elements:[{}]", DataKey, data.elements.mkString(","))
      data.elements.foreach(getOrCreate)
  }
}
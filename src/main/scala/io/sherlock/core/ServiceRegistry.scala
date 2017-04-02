package io.sherlock.core

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{ DistributedData, ORSet, ORSetKey }
import akka.util.Timeout

import scala.concurrent.duration._

object ServiceRegistry {
  def props = Props(new ServiceRegistry)

  case class Get(contextRoot: String)

  def rootToName(root: String): String = root.replaceAll("/", "_")
}

class ServiceRegistry extends Actor with ActorLogging {
  import ServiceRegistry._

  implicit val timeout: Timeout = 3.seconds
  implicit val node = Cluster(context.system)

  val DataKey = ORSetKey[String](self.path.name)

  val replicator = DistributedData(context.system).replicator
  replicator ! Subscribe(DataKey, self)

  override def preStart(): Unit =
    log.info("Start root actor: {}", self.path.name)

  def getOrCreate(serviceName: String): ActorRef =
    context.child(serviceName).getOrElse(context.actorOf(Service.props, serviceName))

  def getOrCreateAndSubscribe(serviceName: String): ActorRef =
    context.child(serviceName).getOrElse {
      log.info("Observe a new service:  {}", serviceName)
      replicator ! Update(DataKey, ORSet(), WriteLocal)(_ + serviceName)
      context.actorOf(Service.props, serviceName)
    }

  override def receive = {
    case hb @ HeartBeat(_, path, _) ⇒
      val serviceName = rootToName(path)
      //log.info(s"HeartBeat $hb for $serviceName")
      getOrCreateAndSubscribe(serviceName) ! hb
    case c @ Changed(DataKey) ⇒
      val data = c.get(DataKey)
      log.info(s"[Replication] by key:{} data:{}", DataKey, data.elements)
      data.elements.foreach(getOrCreate)
    case Get(path) ⇒
      val rootName = rootToName(path)
      log.info("Get root name:{}", rootName)
      //context.children.foreach(log.info("Child: {}", _))
      context.child(rootName) match {
        case None ⇒ sender() ! Service.Result(Map.empty)
        case Some(instance) ⇒
          //log.info("forward GetAccuracy to {}", instance)
          instance forward Service.GetAccuracy
      }
  }
}
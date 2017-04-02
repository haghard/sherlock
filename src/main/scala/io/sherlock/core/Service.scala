package io.sherlock.core

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

object Service {
  def props = Props(new Service())

  case object GetAccuracy

  case class Result(accuracy: Map[String, Double])
}

class Service extends Actor with ActorLogging {
  import Service._
  import context.dispatcher

  private val timeout = 3.seconds
  private val writeMajority = WriteMajority(timeout)

  val replicator = DistributedData(context.system).replicator
  implicit val node = Cluster(context.system)

  val DataKey = ORSetKey[String](self.path.name)
  replicator ! Subscribe(DataKey, self)

  implicit val askTimeout: Timeout = 3.seconds

  override def preStart(): Unit = {
    //_users_v1.0
    //_users_v1.1
    log.info("Start Service {}", self.path.name)
  }

  def getOrCreate(serviceInstanceName: String): ActorRef =
    context.child(serviceInstanceName)
      .getOrElse(context.actorOf(ServiceInstance.props, serviceInstanceName))

  def getOrCreateAndSubscribe(serviceInstanceName: String): ActorRef =
    context.child(serviceInstanceName).getOrElse {
      log.info("*** ServiceName {}", serviceInstanceName)
      replicator ! Update(DataKey, ORSet(), writeMajority /*WriteLocal*/ )(_ + serviceInstanceName)
      context.actorOf(ServiceInstance.props, serviceInstanceName)
    }

  override def receive = {
    case hb @ HeartBeat(ip, path, port) ⇒
      val serviceInstanceName = s"$ip:$port"
      getOrCreateAndSubscribe(serviceInstanceName) ! hb
    case GetAccuracy ⇒
      val futures = Future.traverse(context.children) { child ⇒
        (child ? GetAccuracy).mapTo[ServiceInstance.Accuracy].map(a ⇒ child.path.name → a.percentage)
      }
      futures.map(_.toMap).map(Result).pipeTo(sender())
    case c @ Changed(DataKey) ⇒
      val data = c.get(DataKey)
      log.info("[Replication] by key:{} data:{}", DataKey, data)
      data.elements.foreach(getOrCreate)

    case ModifyFailure(DataKey, error, cause, Some(replyTo: ActorRef)) ⇒
      log.error(cause, error)
    // ReadMajority failure, try again with local read
    //replicator ! Update(DataKey, ORSet(), writeMajority /*WriteLocal*/ )(_ + serviceInstanceName)
    //context.actorOf(ServiceInstance.props, serviceInstanceName)
  }
}


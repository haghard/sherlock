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
  case object GetAccuracy
  case class Result(accuracy: Map[String, Double])

  def props = Props(new Service)
}

/*
_users_v1.0
_users_v1.1
*/
class Service extends Actor with ActorLogging {
  import Service._
  import context.dispatcher

  private val timeout = 3.seconds
  private val writeMajority = WriteMajority(timeout)

  implicit val node = Cluster(context.system)

  val replicator = DistributedData(context.system).replicator
  val DataKey = ORSetKey[String](self.path.name)
  replicator ! Subscribe(DataKey, self)

  implicit val askTimeout: Timeout = 3.seconds

  override def preStart(): Unit =
    log.info("Start Service {}", self.path.name)

  def getOrCreate(serviceTSName: String): ActorRef =
    context.child(serviceTSName).getOrElse(context.actorOf(ServiceInstance.props, serviceTSName))

  def getOrCreateAndSubscribe(serviceTSName: String): ActorRef =
    context.child(serviceTSName).getOrElse {
      log.info("service-name {}", serviceTSName)
      replicator ! Update(DataKey, ORSet(), writeMajority)(_ + serviceTSName)
      context.actorOf(ServiceInstance.props, serviceTSName)
    }

  override def receive = {
    case hb @ HeartBeat(ip, _, port) ⇒
      val serviceTSName = s"$ip:$port"
      getOrCreateAndSubscribe(serviceTSName) ! hb
    case GetAccuracy ⇒
      val f = Future.traverse(context.children) { serviceTs ⇒
        (serviceTs ? GetAccuracy).mapTo[ServiceInstance.Accuracy]
          .map(a ⇒ serviceTs.path.name → a.percentage)
      }.map(_.toMap).map(Result)
      f.pipeTo(sender())
    case c @ Changed(DataKey) ⇒
      val data = c.get(DataKey)
      log.info("[Service-Replication] by key:{} elements:[{}]", DataKey, data.elements.mkString(","))
      data.elements.foreach(getOrCreate)
    case ModifyFailure(DataKey, error, cause, Some(replyTo: ActorRef)) ⇒
      log.error(cause, error)
    // ReadMajority failure, try again with local read
    //replicator ! Update(DataKey, ORSet(), writeMajority /*WriteLocal*/ )(_ + serviceInstanceName)
    //context.actorOf(ServiceInstance.props, serviceInstanceName)
  }
}


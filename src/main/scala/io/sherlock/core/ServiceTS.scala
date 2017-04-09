package io.sherlock.core

import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.ddata.{ DistributedData, ORSet, ORSetKey, Replicator }
import io.sherlock.detector.PhiAccrualFailureDetector

object ServiceTS {
  case class Accuracy(percentage: Double)

  def props = Props(new ServiceTS)
}

class ServiceTS extends Actor with ActorLogging {
  import ServiceTS._

  import scala.concurrent.duration._

  implicit val node = Cluster(context.system)
  implicit val clock = PhiAccrualFailureDetector.defaultClock

  val maxSize = 1000
  val TimeStampsDataKey = ORSetKey[Long](self.path.name)
  val replicator = DistributedData(context.system).replicator

  //service-instance has been started 192.168.0.1:9000
  override def preStart(): Unit =
    log.info("service-instance has been started {}", self.path.name)

  def updateHeartBeat(heartBeats: ORSet[Long]): ORSet[Long] = {
    val newHeartBeat = System.currentTimeMillis
    val truncated =
      if (heartBeats.size > maxSize) heartBeats - heartBeats.elements.toList.sorted.head
      else heartBeats
    log.info("{} hb {} ", self.path.name, newHeartBeat)
    truncated + newHeartBeat
  }

  override def receive = {
    case _: HeartBeat ⇒
      replicator ! Update(TimeStampsDataKey, ORSet.empty[Long], WriteLocal)(updateHeartBeat)
    case Service.GetAccuracy ⇒
      log.info("get-accuracy for service: {}", self.path.name)
      replicator ! Replicator.Get(TimeStampsDataKey, ReadLocal, Some(sender()))
    case g @ GetSuccess(TimeStampsDataKey, Some(replyTo: ActorRef)) ⇒
      val data = g.get(TimeStampsDataKey)
      val timestamps = data.elements.toList
      val phi = PhiAccrualFailureDetector(timestamps.sorted.toIndexedSeq).phi
      log.info("{} get-accuracy {}", self.path.name, phi)
      replyTo ! Accuracy(phi)

    case NotFound(TimeStampsDataKey, Some(replyTo: ActorRef)) ⇒
      log.info("NotFound {}", TimeStampsDataKey)
      replyTo ! Accuracy(Double.PositiveInfinity)

    case GetFailure(TimeStampsDataKey, Some(replyTo: ActorRef)) ⇒
      log.info("ReadMajority failure, try again with local read")
      // ReadMajority failure, try again with local read
      replicator ! Get(TimeStampsDataKey, ReadLocal, Some(replyTo))
  }
}

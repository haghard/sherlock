package io.sherlock.core

import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.ddata.{ DistributedData, ORSet, ORSetKey, Replicator }
import io.sherlock.detector.PhiAccrualFailureDetector

object ServiceInstance {
  case class Accuracy(percentage: Double)

  def props = Props(new ServiceInstance)
}

class ServiceInstance extends Actor with ActorLogging {
  import ServiceInstance._

  import scala.concurrent.duration._
  private val timeout = 2.seconds
  //private val readMajority = ReadMajority(timeout)
  //private val writeMajority = WriteMajority(timeout)

  implicit val node = Cluster(context.system)
  val DataKey = ORSetKey[Long](self.path.name)
  implicit val clock = PhiAccrualFailureDetector.defaultClock
  val replicator = DistributedData(context.system).replicator

  //var upProbability = 100
  val maxSize = 1000

  override def preStart(): Unit = {
    log.info("Instance has been started {}", self.path.name)
  }

  def updateHeartBeat(heartBeats: ORSet[Long]): ORSet[Long] = {
    val newHeartBeat = System.currentTimeMillis
    val truncated =
      if (heartBeats.size > maxSize) heartBeats - heartBeats.elements.toList.sorted.head
      else heartBeats
    log.info("{} new hb:{} ", self.path.name, newHeartBeat)
    truncated + newHeartBeat
  }

  override def receive = {
    case _: HeartBeat ⇒
      replicator ! Update(DataKey, ORSet.empty[Long], /*writeMajority*/ WriteLocal)(updateHeartBeat)
    case Service.GetAccuracy ⇒
      log.info("GetAccuracy for service: {}", self.path.name) //192.168.0.1:9000
      replicator ! Replicator.Get(DataKey, /*readMajority*/ ReadLocal, Some(sender()))
    case g @ GetSuccess(DataKey, Some(replyTo: ActorRef)) ⇒
      val data = g.get(DataKey)
      val timestamps = data.elements.toList
      val accuracy = PhiAccrualFailureDetector(timestamps.sorted.toIndexedSeq).phi
      log.info("{} GetAccuracy response {}", self.path.name, accuracy)
      replyTo ! Accuracy(accuracy)

    case NotFound(DataKey, Some(replyTo: ActorRef)) ⇒
      log.info("NotFound {}", DataKey)
      replyTo ! Accuracy(Double.PositiveInfinity)

    case GetFailure(DataKey, Some(replyTo: ActorRef)) ⇒
      log.info("ReadMajority failure, try again with local read")
      // ReadMajority failure, try again with local read
      replicator ! Get(DataKey, ReadLocal, Some(replyTo))
  }
}

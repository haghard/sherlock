package io.sherlock.core

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{ DistributedData, ORSet, ORSetKey, Replicator }
import io.sherlock.detector.PhiAccrualFailureDetector

object ServiceInstance {

  case class Accuracy(percentage: Double)

  def props = Props(new ServiceInstance)
}

class ServiceInstance extends Actor with ActorLogging {
  import ServiceInstance._

  implicit val node = Cluster(context.system)
  implicit val clock = PhiAccrualFailureDetector.defaultClock

  val maxSize = 1000
  val serviceInstanceDataKey = ORSetKey[Long](self.path.name)
  val replicator = DistributedData(context.system).replicator

  override def preStart(): Unit =
    log.info("service-instance has been started {}", self.path.name)

  def updateHeartBeat(heartBeats: ORSet[Long], instanceSpan: brave.Span): ORSet[Long] = {
    val newHeartBeat = System.currentTimeMillis
    val truncated = if (heartBeats.size > maxSize) heartBeats - heartBeats.elements.toList.sorted.head else heartBeats
    log.info("{} hb {} ", self.path.name, newHeartBeat)
    instanceSpan.finish()
    truncated + newHeartBeat
  }

  def await: Receive = {
    case m @ HeartBeatTrace(hb, cxt, tracer) ⇒
      val instanceSpan = tracer.newChild(cxt).name("instance").start()
      replicator ! Update(serviceInstanceDataKey, ORSet.empty[Long], WriteLocal) { beats ⇒ updateHeartBeat(beats, instanceSpan) }
      context become active
  }

  def active: Receive = {
    case Service.GetAccuracy ⇒
      log.info("get-accuracy for service: {}", self.path.name)
      replicator ! Replicator.Get(serviceInstanceDataKey, ReadLocal, Some(sender()))
    case g @ GetSuccess(`serviceInstanceDataKey`, Some(replyTo: ActorRef)) ⇒
      val data = g.get(serviceInstanceDataKey)
      val timestamps = data.elements.toList
      val phi = PhiAccrualFailureDetector(timestamps.sorted.toIndexedSeq).phi
      log.info("{} get-accuracy {}", self.path.name, phi)
      replyTo ! Accuracy(phi)

    case NotFound(`serviceInstanceDataKey`, Some(replyTo: ActorRef)) ⇒
      log.info("NotFound {}", serviceInstanceDataKey)
      replyTo ! Accuracy(Double.PositiveInfinity)

    case GetFailure(`serviceInstanceDataKey`, Some(replyTo: ActorRef)) ⇒
      log.info("ReadMajority failure, try again with local read")
      // ReadMajority failure, try again with local read
      replicator ! Get(serviceInstanceDataKey, ReadLocal, Some(replyTo))
  }

  override def receive = await
}

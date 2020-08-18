package io.sherlock.stages

import akka.actor.ActorRef
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, StageLogging}
import io.sherlock.stages.ActorBasedSource.{AssignStageActor, SessionOverflowStrategy}

import scala.collection._
import scala.reflect.ClassTag
import ActorBasedSource._

object ActorBasedSource {

  case class AssignStageActor(actorRef: ActorRef)

  sealed trait SessionOverflowStrategy

  case object DropOldest extends SessionOverflowStrategy

  case object DropNewest extends SessionOverflowStrategy

  case object FailStage extends SessionOverflowStrategy

  final case class Overflow(msg: String) extends RuntimeException(msg)
}

final class ActorBasedSource[T: ClassTag](source: ActorRef, bufferSize: Int, os: SessionOverflowStrategy)
    extends GraphStage[SourceShape[T]] {
  val out: Outlet[T]                 = Outlet("actor-source")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      lazy val self: StageActor     = getStageActor(onReceive)
      var queue: immutable.Queue[T] = immutable.Queue.empty[T]

      override def preStart(): Unit =
        source ! AssignStageActor(self.ref)

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            log.info("onPull() called...")
            tryToPush()
          }
        }
      )

      private def tryToPush(): Unit =
        if (isAvailable(out) && queue.nonEmpty)
          //log.info("ready to dequeue")
          queue.dequeue match {
            case (msg: T, rest: immutable.Queue[T]) ⇒
              log.info("got message from queue, pushing: {} ", msg)
              push(out, msg)
              queue = rest
          }

      private def onReceive(x: (ActorRef, Any))(implicit tag: ClassTag[T]): Unit =
        x match {
          case (sender, tag(msg)) ⇒
            log.info("incoming msg from {}, queueing: {} ", sender, msg)
            queue =
              if (queue.size < bufferSize) queue.enqueue(msg)
              else
                os match {
                  case DropOldest ⇒
                    if (queue.isEmpty) queue.enqueue(msg) else queue.tail.enqueue(msg)
                  case DropNewest ⇒
                    queue
                  case FailStage ⇒
                    failStage(Overflow(s"Received messages are more than $bufferSize"))
                    immutable.Queue.empty[T]
                }
            tryToPush()
          case (sender, unexpectedMessage) ⇒
            log.error("Unsupported type from {}, dropping:  {} ", sender, unexpectedMessage.getClass.getName)
        }
    }
}

package io.sherlock.stages

import akka.actor.ActorRef
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{ GraphStage, GraphStageLogic, OutHandler, StageLogging }
import akka.stream.{ Attributes, Outlet, SourceShape }
import io.sherlock.stages.PubSubBasedSource.AssignStageActor

import scala.collection.immutable.Queue
import scala.reflect.ClassTag

object PubSubBasedSource {
  case class AssignStageActor(actorRef: ActorRef)
}

class PubSubBasedSource[T: ClassTag](source: ActorRef) extends GraphStage[SourceShape[T]] {
  val out: Outlet[T] = Outlet("actor-source")
  override val shape: SourceShape[T] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      lazy val self: StageActor = getStageActor(onMessage)
      var buffer: Queue[T] = Queue()

      override def preStart(): Unit = {
        source ! AssignStageActor(self.ref)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          log.info("onPull() called...")
          tryToPush()
        }
      })

      private def tryToPush(): Unit = {
        if (isAvailable(out) && buffer.nonEmpty) {
          //log.info("ready to dequeue")
          buffer.dequeue match {
            case (msg: T, rest: Queue[T]) ⇒
              log.info("got message from queue, pushing: {} ", msg)
              push(out, msg)
              buffer = rest
          }
        }
      }

      private def onMessage(x: (ActorRef, Any))(implicit tag: ClassTag[T]): Unit = {
        x match {
          case (sender, tag(msg)) ⇒
            log.info("incoming msg from {}, queueing: {} ", sender, msg)
            buffer = buffer enqueue msg
            tryToPush()
          case (sender, unexpectedMessage) ⇒
            log.error("Unsupported type from {}, dropping:  {} ", sender, unexpectedMessage.getClass.getName)
        }
      }
    }
}
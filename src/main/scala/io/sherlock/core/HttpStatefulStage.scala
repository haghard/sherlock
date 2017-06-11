package io.sherlock.core

import java.util.concurrent.atomic.AtomicReference
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest }
import akka.stream.stage._
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }

class UniqueHostsStage(val hosts: AtomicReference[Set[String]]) extends GraphStage[FlowShape[HttpRequest, HttpRequest]] {
  val in  = Inlet[HttpRequest]("in")
  val out = Outlet[HttpRequest]("out")

  override def shape = FlowShape.of(in, out)

  def findHost(headers: Seq[HttpHeader]): Option[Host] = {
    def loop(it: Iterator[HttpHeader]): Option[Host] =
      if (it.hasNext) {
        it.next match {
          case h: Host ⇒ Some(h)
          case _       ⇒ loop(it)
        }
      } else None
    loop(headers.iterator)
  }

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with StageLogging {
      setHandlers(
        in,
        out,
        new InHandler with OutHandler {
          def capture(hosts: AtomicReference[Set[String]], candidate: String) = {
            def loop(hosts: AtomicReference[Set[String]], candidate: String): Unit = {
              val current = hosts.get
              val updated = current + candidate
              if (!hosts.compareAndSet(current, updated)) loop(hosts, candidate)
            }
            loop(hosts, candidate)
          }

          override def onPush(): Unit = {
            val nextReq = grab(in)
            val host    = findHost(nextReq.headers)
            host.foreach { h =>
              capture(hosts, h.host.address)
            }
            log.info(s"hosts: ${hosts.get().mkString(",")}")
            push(out, nextReq)
          }

          override def onPull() = pull(in)
        }
      )
    }
}

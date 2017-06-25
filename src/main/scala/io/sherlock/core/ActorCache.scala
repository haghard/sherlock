package io.sherlock.core

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.PathMatcher.{ Matched, Unmatched }
import akka.http.scaladsl.server.PathMatcher1
import akka.pattern.ask
import akka.stream.scaladsl.{ Flow, GraphDSL }
import akka.stream.stage._
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import io.sherlock.core.ActorCache.{ Ans, Get }

import scala.util._

object ActorCache {

  case class Get(r: HttpRequest)

  case class Ans(r: Boolean)

  def props = Props[ActorCache]

  def flow(
    checkStage: GraphStage[FlowShape[HttpRequest, HttpRequest]],
    http:       Flow[HttpRequest, HttpResponse, akka.NotUsed]
  ) = {
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val check = b.add(checkStage)
      val routes = b.add(http)
      check ~> routes
      FlowShape(check.in, routes.out)
    })
  }
}

class ActorCache extends Actor with ActorLogging {
  var count: Long = 0l

  override def receive: Receive = {
    case r: Get ⇒ sender() ! Ans(true)
    case req: HttpRequest ⇒
      count = count + 1
      log.info("count: {}", count)
      sender() ! req
  }
}

class CacheStage(actor: ActorRef)(implicit t: akka.util.Timeout) extends GraphStage[FlowShape[HttpRequest, HttpRequest]] {
  private val in = Inlet[HttpRequest]("in")
  private val out = Outlet[HttpRequest]("out")

  val matcher = """/user/(\d+)/(.+)""".r

  override val shape = FlowShape.of(in, out)

  import akka.http.scaladsl.model._

  override def createLogic(atts: Attributes) =
    new GraphStageLogic(shape) with StageLogging {
      var cb: AsyncCallback[Try[Ans]] = _

      private def onResponse(res: Try[Ans]) = push(out, HttpRequest())

      //http://doc.akka.io/docs/akka-http/10.0.4/java/http/server-side/low-level-server-side-api.html
      setHandler(in, new InHandler {
        override def onPush() = {
          val req = grab(in)
          if (req.method == HttpMethods.GET) {
            val uri = req.uri.path.toString
            uri match {
              case matcher(id, other) ⇒
                log.info("id {} needs to be cheched", id)
                cb = getAsyncCallback[Try[Ans]](onResponse)
                (actor ? req).mapTo[Ans].onComplete(cb.invoke)(materializer.executionContext)
              case _ ⇒ push(out, req)
            }
          } else push(out, req)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull() = pull(in)
      })

      /*setHandlers(in, out, new InHandler with OutHandler {
        override def onPush(): Unit = {
          val req = grab(in)
          cb = getAsyncCallback[Try[Ans]](onResponse)
          (actor ? req).mapTo[Ans].onComplete(cb.invoke)(materializer.executionContext)
        }

        override def onPull() = pull(in)
      })*/
    }
}
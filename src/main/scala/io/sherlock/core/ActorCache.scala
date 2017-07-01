package io.sherlock.core

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import io.sherlock.core.ActorCache.{Ans, Check}

import scala.util._

object ActorCache {

  case class Check(req: HttpRequest, userId: Long)

  case class Ans(r: Boolean, userId: Long, req: HttpRequest)

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
    case r: Check ⇒
      count = count + 1
      log.info("count: {}", count)
      sender() ! Ans(true, r.userId, r.req)
  }
}

/*
 * This stage extracts userId from path and validate it against the ActorCache
 */
class CacheStage(actor: ActorRef)(implicit t: akka.util.Timeout) extends GraphStage[FlowShape[HttpRequest, HttpRequest]] {
  import akka.http.scaladsl.model._

  private val in = Inlet[HttpRequest]("in")
  private val out = Outlet[HttpRequest]("out")

  val matcher = """/user/(\d+)/(.+)""".r

  override val shape = FlowShape.of(in, out)

  override def createLogic(atts: Attributes) =
    new GraphStageLogic(shape) with StageLogging {
      var callback: AsyncCallback[Try[Ans]] = _

      private def onResponse(res: Try[Ans]) = {
        res.fold({ ex ⇒
          log.error(ex, "User check error ")
          completeStage()
        }, { ans ⇒
          if (ans.r) push(out, ans.req.withHeaders(RawHeader("userId", ans.userId.toString)))
          else push(out, ans.req)
        })
      }

      //http://doc.akka.io/docs/akka-http/10.0.4/java/http/server-side/low-level-server-side-api.html
      setHandler(in, new InHandler {
        override def onPush() = {
          val req = grab(in)
          if (req.method == HttpMethods.GET) {
            val uri = req.uri.path.toString
            uri match {
              case matcher(id, _) ⇒
                val userId = id.toLong
                log.info("id {} needs to be checked", userId)
                callback = getAsyncCallback[Try[Ans]](onResponse)
                (actor ? Check(req, userId)).mapTo[Ans].onComplete(callback.invoke)(materializer.executionContext)
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
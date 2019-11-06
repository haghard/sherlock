package io.sherlock.stages

import akka.actor.ActorRef
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage._
import io.sherlock.core.ActorCache.{ Ans, Check }
import akka.pattern.ask

import scala.collection.immutable.LinearSeq
import scala.collection.mutable.ListBuffer
import scala.util.Try

//  http GET :9091/user/1/stat

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
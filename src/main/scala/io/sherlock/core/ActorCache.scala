package io.sherlock.core

import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.{ Flow, GraphDSL }
import akka.stream.stage._
import akka.stream.FlowShape
import io.sherlock.core.ActorCache.{ Ans, Check }

object ActorCache {

  case class Check(req: HttpRequest, userId: Long)

  case class Ans(r: Boolean, userId: Long, req: HttpRequest)

  def props = Props[ActorCache]

  def flow(
    checkStage: GraphStage[FlowShape[HttpRequest, HttpRequest]],
    http:       Flow[HttpRequest, HttpResponse, akka.NotUsed]) = {
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
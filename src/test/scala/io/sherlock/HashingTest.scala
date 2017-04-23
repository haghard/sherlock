package io.sherlock

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.routing.ConsistentHashingRouter.ConsistentHashMapping
import akka.routing.{ ActorRefRoutee, ConsistentHashingRoutingLogic, Routee }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }

import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }

import scala.collection.immutable

class HashingTest extends TestKit(ActorSystem()) with WordSpecLike with ImplicitSender with BeforeAndAfterAll {

  class ChRoutee(ind: Int) extends Actor with ActorLogging {
    var count = 0
    var keys = Set[String]()

    override def postStop(): Unit = {
      println(ind + " - " + count)
      //println(ind + " - " + keys.mkString(","))
    }

    override def receive: Receive = {
      case i: Int ⇒
        count = count + 1
      case key: String ⇒
        keys = keys + key
    }
  }

  def props(ind: Int) = Props(new ChRoutee(ind))

  override def beforeAll(): Unit = {
  }

  override def afterAll(): Unit = {
    //import scala.concurrent.ExecutionContext.Implicits.global
    system.terminate()
  }

  val currentRouteeSize = 5
  val keys = Vector("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o")

  "Router" should {
    "handle messages" in {
      val hashMapping: ConsistentHashMapping = {
        case i: Int      ⇒ i
        case key: String ⇒ key
      }

      val routees: immutable.IndexedSeq[Routee] =
        Vector(
          new ActorRefRoutee(system.actorOf(props(0))),
          new ActorRefRoutee(system.actorOf(props(1))),
          new ActorRefRoutee(system.actorOf(props(2))),
          new ActorRefRoutee(system.actorOf(props(3))),
          new ActorRefRoutee(system.actorOf(props(4)))
        )

      val logic = ConsistentHashingRoutingLogic(system, currentRouteeSize * 5, hashMapping)

      val probe = TestProbe("ch-router")

      (0 until 100).foreach { i ⇒
        Thread.sleep(50)
        //val key = keys((i % keys.size))
        logic.select(i, routees).send(i, probe.ref)
      }

      1 === 1
    }
  }
}

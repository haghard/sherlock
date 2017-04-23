package io.sherlock

import java.util
import java.util.Map
import java.util.concurrent.atomic.AtomicInteger

import io.sherlock.Hashing.{ConsistentHashing, HashingRouter, RendezvousHashing}

object CompareHashing {
  val keysNum = 500000

  val nodes0 = "alpha-1" :: "beta-2" :: "gamma-3" :: "delta-4" :: "epsilon-5" :: "zeta-6" :: "eta-7" ::
    "theta-8" :: "iota-9" :: "kappa-10" :: Nil

  private def getNodes(distribution: util.Map[String, AtomicInteger]) = {
    val nodes = new java.util.ArrayList[String]
    (0 until nodes0.size).foreach { i ⇒
      val node = nodes0(i)
      nodes.add(node)
      distribution.put(node, new AtomicInteger)
    }
    nodes
  }

  def iter(router: HashingRouter[String, _], distribution: Map[String, AtomicInteger]) = {
    (0 until keysNum).foreach { i ⇒
      distribution.get(router.get(s"my-long-key-$i")).incrementAndGet()
    }

    val iter = distribution.entrySet().iterator()
    while (iter.hasNext) {
      val e = iter.next
      println(e.getKey + "," + e.getValue.get)
      e.getValue.set(0)
    }

    println("====== remove ========")

    (0 until 4).foreach { i ⇒
      val node = nodes0(i)
      router.remove(node)
      distribution.remove(node)
    }

    (0 until keysNum).foreach { i ⇒
      distribution.get(router.get(s"my-long-key-$i")).incrementAndGet()
    }

    println("====== stats ========")
    val iter1 = distribution.entrySet().iterator()
    while (iter1.hasNext) {
      val e = iter1.next
      println(e.getKey() + "," + e.getValue().get())
    }
  }

  def main(args: Array[String]): Unit = {
    println("======: ConsistentHash :========")
    val distribution0: Map[String, AtomicInteger] = new util.HashMap[String, AtomicInteger]()
    iter(HashingRouter[String, ConsistentHashing](getNodes(distribution0)), distribution0)

    println("======: RendezvousHashing :========")
    val distribution1: Map[String, AtomicInteger] = new util.HashMap[String, AtomicInteger]()
    iter(HashingRouter[String, RendezvousHashing](getNodes(distribution1)), distribution1)

/*
    val vnodes = 5
    val replicas = Cache("alpha") :: Cache("beta") :: Cache("gamma") :: Cache("delta") ::
      Cache("epsilon") :: Cache("zeta") :: Cache("eta") ::
      Cache("theta") :: Cache("iota") :: Cache("kappa") :: Nil

    val ch = ConsistentHash[String, Cache](new MD5HashFunction, vnodes, replicas)

    ch.get(Some("key-a"))
    ch.get(Some("key-a"))
*/

  }
}
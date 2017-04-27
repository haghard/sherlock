package io.sherlock

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util
import java.util.SortedMap
import java.util.concurrent.ConcurrentSkipListSet

import scala.collection.immutable.SortedSet
import scala.reflect.ClassTag
import java.util.{SortedMap => JSortedMap, TreeMap => JTreeMap}

/*

t: ClassTag[Node],
import java.util
import io.sherlock.Hashing._

val h = HashingRouter[String, ConsistentHashing](new util.ArrayList[String](java.util.Arrays.asList("192.168.0.1","192.168.0.2")))
val h = HashingRouter[String, RendezvousHashing](new util.ArrayList[String](java.util.Arrays.asList("192.168.0.1","192.168.0.2")))
h.add("192.168.0.3")

h.get("key_a")
h.get("key_b")

*/

import com.roundeights.hasher.Implicits._
import scala.language.postfixOps

object Hashing {

  sealed trait Alg

  trait RendezvousHashing extends Alg

  trait ConsistentHashing extends Alg

  trait HashingAlg[N, A <: Alg] {
    protected val hasher = scala.util.hashing.MurmurHash3
    protected val salt0 = "sdfw3w345twergt34awedfszd"

    def hash(bytes: Array[Byte]): Int = {
      val digest = java.security.MessageDigest.getInstance("SHA-256")
      digest.update("password".getBytes("UTF-8"))
      digest.digest(bytes)
    }

    def initNodes(nodes: util.Collection[N]): Unit

    def toBinary(node: N): Array[Byte]

    def removeNode(node: N): Boolean

    def addNode(node: N): Boolean

    def read(key: String, rf: Int): Set[N]

    def validated(node: N): Boolean
  }

  //Highest Random Weight (HRW) hashing
  //https://github.com/clohfink/RendezvousHash
  trait Rendezvous[N, A <: Alg] extends HashingAlg[N, A] {
    protected var members: ConcurrentSkipListSet[N] = _

    case class Item(hash: Int, node: N)

    override def initNodes(nodes: util.Collection[N]): Unit =
      members = new ConcurrentSkipListSet[N](nodes)

    override def removeNode(node: N): Boolean = {
      println(s"remove $node")
      members.remove(node)
    }

    override def addNode(node: N): Boolean = {
      if (validated(node)) {
        println(s"remove $node")
        members.add(node)
      } else false
    }

    override def read(key: String, rf: Int): Set[N] = {
      var allHashes = SortedSet.empty[Item](new Ordering[Item]() {
        override def compare(x: Item, y: Item): Int =
          -x.hash.compare(y.hash)
      })
      val iter = members.iterator
      while (iter.hasNext) {
        val node = iter.next
        val a = key.getBytes
        val b = toBinary(node)
        val bytes = ByteBuffer.allocate(a.length + b.length).put(a).put(b).array()
        //val hash =  new BigInteger(1, bytes.md5.bytes).intValue()
        val hash = hasher.arrayHash(bytes)
        allHashes = allHashes + Item(hash, node)
      }
      allHashes.take(rf).map(_.node)
    }
  }

  //https://infinitescript.com/2014/10/consistent-hash-ring/
  trait ConsistentHash[Node, A <: Alg] extends HashingAlg[Node, A] {
    private val ring: JSortedMap[Int, Node] = new JTreeMap[Int, Node]()

    override def initNodes(nodes: util.Collection[Node]): Unit = {
      val iter = nodes.iterator()
      while (iter.hasNext) {
        addNode(iter.next)
      }
    }

    override def removeNode(node: Node): Boolean = {
      val bytes = toBinary(node)
      //val hash =  new BigInteger(1, bytes.md5.bytes).intValue()
      val hash = hasher.arrayHash(bytes)
      println(s"remove $node - $hash")
      node == ring.remove(hash)
    }

    override def addNode(node: Node): Boolean = {
      if (validated(node)) {
        val bytes = toBinary(node)
        //val hash =  new BigInteger(1, bytes.md5.bytes).intValue()
        val hash = hasher.arrayHash(bytes)
        println(s"add $node - $hash")
        ring.put(hash, node)
        true
      } else false
    }

    override def read(key: String, rf: Int): Set[Node] = {
      val bytes = key.getBytes
      //var hash =  new BigInteger(1, bytes.md5.bytes).intValue()
      var hash = hasher.arrayHash(bytes)
      if (!ring.containsKey(hash)) {
        val tailMap = ring.tailMap(hash)
        hash = if (tailMap.isEmpty) ring.firstKey else tailMap.firstKey
      }
      Set(ring.get(hash))
    }
  }

  object HashingAlg {

    implicit val chString = new ConsistentHash[String, ConsistentHashing] {
      override def toBinary(node: String): Array[Byte] = node.getBytes

      override def validated(node: String): Boolean = {
        //TODO: Pattern for IpAddress
        true
      }
    }

    implicit val rendezvousString = new Rendezvous[String, RendezvousHashing] {
      override def toBinary(node: String): Array[Byte] = node.getBytes

      override def validated(node: String): Boolean = {
        //TODO: Pattern for IpAddress
        true
      }
    }

    def apply[N, A <: Alg](implicit t: ClassTag[N], alg: HashingAlg[N, A]): HashingAlg[N, A] =
      alg
  }

  trait HashingRouter[N, A <: Alg] {
    type B <: HashingAlg[N, A]

    def alg: B

    def init(nodes: util.Collection[N]): HashingRouter[N, A] = {
      alg.initNodes(nodes)
      this
    }

    def remove(node: N): Boolean =
      alg.removeNode(node)

    def add(node: N): Boolean =
      alg.addNode(node)

    def get(key: String): N =
      alg.read(key, 1).head

    def get(key: String, rf: Int): Set[N] =
      alg.read(key, rf)
  }

  object HashingRouter {
    def apply[Node, A <: Alg](nodes: util.Collection[Node])(implicit hashingAlg: HashingAlg[Node, A]): HashingRouter[Node, A] = {
      val router = new HashingRouter[Node, A] {
        override type B = HashingAlg[Node, A]
        override val alg = hashingAlg
      }.init(nodes)

      router
    }
  }
}

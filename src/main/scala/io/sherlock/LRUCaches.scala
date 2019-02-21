package io.sherlock

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{ Deadline, FiniteDuration }

//  https://stackoverflow.com/questions/23772102/lru-cache-in-java-with-generics-and-o1-operations
object LRUCaches {

  class Node[T, U](var previous: Node[T, U] = null, var next: Node[T, U] = null,
                   val key: T = null.asInstanceOf[T], val value: U = null.asInstanceOf[U])

  class Node0[T, U](
    var next: Node0[T, U] = null,
    val key:  T           = null.asInstanceOf[T], val value: U = null.asInstanceOf[U])

  object LRUCache {
    def apply[K, V](capacity: Int) = {
      val empty = new Node[K, V]()
      new LRUCache(capacity, new java.util.HashMap[K, Node[K, V]](), empty, empty)
    }
  }

  class LRUCache0[K, V](capacity: Int) {
    private val map = new java.util.HashMap[K, V]()
    private val linkedList = new java.util.LinkedList[K]()

    def get(key: K): Option[V] = {
      val targetValue = map.get(key)
      if (targetValue == null) None
      else {
        if (linkedList.peekLast == key)
          Some(targetValue)
        else {
          //O(n)
          linkedList.remove(key)
          linkedList.addLast(key)
          Some(targetValue)
        }
      }
    }

    def put(key: K, value: V): Unit = {
      if (!map.containsKey(key)) {
        linkedList.addLast(key)
        map.put(key, value)

        // Delete the left-most entry and update the LRU pointer
        if (map.size == capacity + 1) {
          val evicted = linkedList.removeFirst
          map.remove(evicted)
        }
      }
    }

    def size = map.size

    override def toString: String = {
      val it = linkedList.iterator
      val sb = new mutable.StringBuilder().append("list: ")
      while (it.hasNext)
        sb.append(it.next)
      sb.toString
    }
  }

  class LRUCache[K, V](capacity: Int, cache: java.util.Map[K, Node[K, V]],
                       var leastRU: Node[K, V], var mostRU: Node[K, V]) {
    private var currentSize: Int = 0

    //  O(1)
    def get(key: K): Option[V] = {
      val targetNode = cache.get(key)
      if (targetNode == null) None
      else if (targetNode.key == mostRU.key) Some(mostRU.value)
      else {
        val nextNode = targetNode.next
        val prevNode = targetNode.previous

        if (targetNode.key == leastRU.key) {
          nextNode.previous = null
          leastRU = nextNode
        } else {
          prevNode.next = nextNode
          nextNode.previous = prevNode
        }

        // Finally move our item to the MRU
        targetNode.previous = mostRU
        mostRU.next = targetNode
        mostRU = targetNode
        mostRU.next = null
        Some(targetNode.value)
      }
    }

    //  O(1)
    def put(key: K, value: V): Unit = {
      if (!cache.containsKey(key)) {
        val newNode = new Node[K, V](mostRU, null, key, value)
        mostRU.next = newNode
        cache.put(key, newNode)
        mostRU = newNode

        // Delete the left-most entry and update the LRU pointer
        if (capacity == currentSize) {
          cache.remove(leastRU.key)
          leastRU = leastRU.next
          leastRU.previous = null
        } // Update cache size, for the first added entry update the LRU pointer
        else if (currentSize < capacity) {
          if (currentSize == 0) {
            leastRU = newNode
          }
          currentSize += 1
        }
      }
    }

    def size = currentSize

    override def toString: String = {
      def loopMap(it: java.util.Iterator[K], sb: mutable.StringBuilder,
                  first: Boolean = false): String = {
        if (it.hasNext)
          if (first) loopMap(it, sb.append(it.next))
          else loopMap(it, sb.append(",").append(it.next))
        else sb.toString
      }

      def loopList(n: Node[K, V], sb: mutable.StringBuilder): String = {
        val sb0 =
          //if (n.key != null && n.value != null) sb.append(n.key).append(",") else sb
          if (n.next != null && n.previous != null) sb.append(n.key).append(",") else sb
        if (n.next != null) loopList(n.next, sb0)
        else sb.append(" - ").toString
      }

      loopList(leastRU, new mutable.StringBuilder().append("list:")) +
        loopMap(cache.keySet.iterator, new mutable.StringBuilder().append("cache:"), true)
    }
  }

  //https://stackoverflow.com/questions/23772102/lru-cache-in-java-with-generics-and-o1-operations
  class LRUCache1[K, V](capacity: Int) {
    val data: java.util.LinkedHashMap[K, V] = new java.util.LinkedHashMap[K, V]()

    def get(key: K): Option[V] =
      Option(data.get(key)).map { value ⇒
        //remove least recently used element (head)
        data.remove(key, value)
        //move the element to the most recently used position (tail)
        data.put(key, value)
        value
      }

    def put(key: K, value: V): Unit = {
      if (!data.containsKey(key)) {
        if (data.keySet.size == capacity) {
          //remove least recently used element (head)
          val it = data.keySet.iterator
          it.next
          it.remove
        }
        //move the element to the most recently used position(tail)
        data.put(key, value)
      }
    }

    override def toString: String = {
      def loop(it: java.util.Iterator[K], sb: mutable.StringBuilder, first: Boolean = false): String = {
        if (it.hasNext)
          if (first) loop(it, sb.append(it.next))
          else loop(it, sb.append(",").append(it.next))
        else sb.toString
      }

      loop(data.keySet.iterator, new mutable.StringBuilder, true)
    }
  }

  //https://www.codewalk.com/2012/04/least-recently-used-lru-cache-implementation-java.html
  class LRULinkedHashMapCache[K, V](capacity: Int) extends java.util.LinkedHashMap[K, V](capacity, 1.0f, true) {
    //Returns true if this map should remove its eldest entry
    override protected def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean =
      size() > capacity

    override def toString: String = {
      def loop(it: java.util.Iterator[K], sb: mutable.StringBuilder,
               first: Boolean = false): String = {
        if (it.hasNext)
          if (first) loop(it, sb.append(it.next))
          else loop(it, sb.append(",").append(it.next))
        else sb.toString
      }

      loop(keySet.iterator, new mutable.StringBuilder, true)
    }
  }

  case object RateLimit extends RuntimeException

  class RateLimiter(numOfReqs: Int, period: FiniteDuration) {
    var seqNumber = 0
    val timesTotalOrder = {
      val onePeriodAgo = Deadline.now - period
      Array.fill(numOfReqs)(onePeriodAgo)
    }

    private def execute(ts: Deadline): Unit = {
      timesTotalOrder(seqNumber) = ts
      seqNumber += 1
      if (seqNumber == numOfReqs)
        seqNumber = 0
    }

    def call[T](b: ⇒ Future[T]): Future[T] = {
      val now = Deadline.now
      if (now - timesTotalOrder(seqNumber) < period)
        Future.failed(RateLimit)
      else {
        execute(now)
        b
      }
    }
  }

  class RequestCache0[T](capacity: Int) {
    private val empty: Node0[String, T] = new Node0[String, T]()
    private val cache: java.util.Map[String, Node0[String, T]] =
      new java.util.HashMap[String, Node0[String, T]]()

    var head: Node0[String, T] = empty
    var tail: Node0[String, T] = empty

    var currentSize: Int = 0

    def put(uuid: String, req: T): Boolean = {
      if (cache.containsKey(uuid)) false
      else {
        val newNode = new Node0[String, T](null, uuid, req)
        cache.put(uuid, newNode)
        tail.next = newNode
        tail = newNode

        if (capacity == currentSize) {
          cache.remove(head.key)
          val prevHead = head
          head = head.next
          prevHead.next = null
          //println(s"${prevHead.key} : ${prevHead.next}")
        } else if (currentSize < capacity) {
          if (currentSize == 0) {
            val prevHead = head
            head = newNode
            prevHead.next = null
          }

          currentSize += 1
        }
        true
      }
    }

    def get(uuid: String): Option[T] =
      Option(cache.get(uuid)).map(_.value)

    override def toString: String = {
      def loopMap(it: java.util.Iterator[String], sb: mutable.StringBuilder, first: Boolean = false): String =
        if (it.hasNext)
          if (first) loopMap(it, sb.append(it.next))
          else loopMap(it, sb.append(",").append(it.next))
        else sb.toString

      def loopList(n: Node0[String, T], sb: mutable.StringBuilder): String = {
        val sb0 = if (n.key != null) sb.append(n.key).append(",") else sb
        if (n.next != null) loopList(n.next, sb0)
        else sb.append(" - ").toString
      }

      loopList(head, new mutable.StringBuilder().append("list:")) +
        loopMap(cache.keySet.iterator, new mutable.StringBuilder().append("cache:"), true)
    }
  }

  //double -linked list
  class RequestCache[T](
    capacity:          Int,
    private val empty: Node[String, T] = new Node[String, T]()) {

    private val cache: java.util.Map[String, Node[String, T]] =
      new java.util.HashMap[String, Node[String, T]]()

    var head: Node[String, T] = empty
    var tail: Node[String, T] = empty

    var currentSize: Int = 0

    def put(uuid: String, req: T): Boolean = {
      if (cache.containsKey(uuid)) false
      else {
        val newNode = new Node[String, T](tail, null, uuid, req)
        cache.put(uuid, newNode)
        tail.next = newNode
        tail = newNode

        if (capacity == currentSize) {
          cache.remove(head.key)
          head = head.next
          head.previous = null
        } else if (currentSize < capacity) {
          if (currentSize == 0)
            head = newNode

          currentSize += 1
        }
        true
      }
    }

    def get(uuid: String): Option[T] =
      Option(cache.get(uuid)).map(_.value)

    override def toString: String = {
      def loopMap(it: java.util.Iterator[String], sb: mutable.StringBuilder, first: Boolean = false): String =
        if (it.hasNext)
          if (first) loopMap(it, sb.append(it.next))
          else loopMap(it, sb.append(",").append(it.next))
        else sb.toString

      def loopList(n: Node[String, T], sb: mutable.StringBuilder): String = {
        val sb0 = if (n.key != null) sb.append(n.key).append(",") else sb
        if (n.next != null) loopList(n.next, sb0)
        else sb.append(" - ").toString
      }

      loopList(head, new mutable.StringBuilder().append("list:")) +
        loopMap(cache.keySet.iterator, new mutable.StringBuilder().append("cache:"), true)
    }
  }

  val c = LRUCache[Symbol, Int](5)

  c.put('a, -1)
  c.put('b, -1)
  c.toString
  c.size

  c.put('c, -1)
  c.put('d, -1)
  c.put('e, -1)
  c.toString
  c.size

  c.put('f, -1)
  c.put('g, -1)
  c.toString
  c.size

  c.get('e)

  c.toString
  c.size

  c.get('g)
  c.toString
  c.size

  //aka java.util.concurrent.ConcurrentSkipListMap
  val memtable = new edu.stanford.ppl.concurrent.SnapTreeMap[Int, String]()

}

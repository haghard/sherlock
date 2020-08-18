package io.sherlock

import io.sherlock.LockFreeQueue.NodeRef
import java.util.concurrent.atomic.AtomicReference

object LockFreeQueue {

  case class NodeRef[T](value: T, next: AtomicReference[NodeRef[T]] = new AtomicReference[NodeRef[T]](null))
}

//LockFreeQueue from scalaz actor https://gist.github.com/djspiewak/671deab9d7ea027cdc42
class LockFreeQueue[T] {
  private val zero = NodeRef[T](null.asInstanceOf[T])

  private val head = new AtomicReference[NodeRef[T]](zero)
  private val tail = new AtomicReference[NodeRef[T]](head.get)

  @scala.annotation.tailrec
  private final def lookFreeEnqLoop(node: NodeRef[T]): Unit = {
    val last = tail.get
    val next = last.next.get
    if (last == tail.get) {
      val r =
        if (next == null)
          if (last.next.compareAndSet(next, node))
            tail.compareAndSet(last, node)
          else false
        else tail.compareAndSet(last, next)
      if (r) () else lookFreeEnqLoop(node)
    } else lookFreeEnqLoop(node)
  }

  @scala.annotation.tailrec
  private final def lockFreeDeqLoop: Option[T] = {
    val first = head.get
    val last  = tail.get
    val next  = first.next.get

    if (first == head.get)
      if (first == last)
        if (next == null) None
        else {
          tail.compareAndSet(last, next)
          lockFreeDeqLoop
        }
      else {
        val v = next.value
        if (head.compareAndSet(first, next)) Some(v)
        else lockFreeDeqLoop
      }
    else lockFreeDeqLoop
  }

  def enq(v: T): Unit =
    lookFreeEnqLoop(NodeRef(v))

  def deq: Option[T] =
    lockFreeDeqLoop
}

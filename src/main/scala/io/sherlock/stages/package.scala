package io.sherlock

package object stages {

  //(E[]) new Object[capacity];
  //new Array[AnyRef](findNextPositivePowerOfTwo(capacity)).asInstanceOf[Array[T]])
  //new Array[AnyRef](findNextPositivePowerOfTwo(capacity)).asInstanceOf[Array[T]])
  class RingBuffer[T: scala.reflect.ClassTag] private(capacity: Int, mask: Int, buffer: Array[T]) {
    private var tail: Long = 0l
    private var head: Long = 0l

    def this(capacity: Int) {
      this(nextPowerOfTwo(capacity), nextPowerOfTwo(capacity) - 1, Array.ofDim[T](nextPowerOfTwo(capacity)))
    }

    private def nextPowerOfTwo(value: Int): Int = {
      1 << (32 - Integer.numberOfLeadingZeros(value - 1))
    }

    def offer(e: T): Boolean = {
      val wrapPoint = tail - capacity
      if(head <= wrapPoint) false
      else {
        buffer(tail.toInt & mask) = e
        tail = tail + 1
        true
      }
    }

    def poll(): Option[T] = {
      if (head >= tail) None
      else {
        val index = head.toInt & mask
        val element: T = buffer(index)
        buffer(index) = null.asInstanceOf[T]
        head = head + 1
        Some(element)
      }
    }

    override def toString =
      s"nextHead: [$head/${head.toInt & mask}] nextTail:[$tail/${tail.toInt & mask}] buffer: ${buffer.mkString(",")}"
  }

  //val buffer = new RingBuffer[Int](6)
  //buffer.offer(1)
}

package io.sherlock

import scala.collection.mutable.ArrayBuffer

object Btrees {

  trait BTree[+T]

  case object BLeaf extends BTree[Nothing]

  case class BNode[T](value: T, left: BTree[T], right: BTree[T]) extends BTree[T]

  implicit class TreeSyntax[T](self: BTree[T])(implicit ord: scala.math.Ordering[T]) {
    @scala.annotation.tailrec private def search(searched: T, t: BTree[T], n: Long = 0): (Option[T], Long) = t match {
      case BLeaf                           ⇒ (None, n)
      case BNode(v, _, _) if searched == v ⇒ (Option(v), n)
      case BNode(v, left, right) ⇒
        if (ord lt (searched, v)) search(searched, left, n + 1)
        else search(searched, right, n + 1)
    }

    def foreach[B](f: T ⇒ B): Unit = {
      @annotation.tailrec
      def go(tree: BTree[T], stack: List[BTree[T]], f: T ⇒ B, n: Long = 0): Unit =
        tree match {
          case BNode(v, l, r @ BNode(_, _, _)) ⇒
            val stack0 = r :: stack
            f(v)
            //println(s" stack: ${stack0.mkString(",")}")
            go(l, stack0, f, n + 1)
          case BNode(v, l, BLeaf) ⇒
            f(v)
            //println(s" stack: ${stack.mkString(",")}")
            go(l, stack, f, n + 1)
          case _ ⇒
            if (stack.size > 0) go(stack.head, stack.tail, f, n)
            else println(n)
        }

      go(self, Nil, f, 0)
    }

    def preOrder[B](f: T ⇒ B): Unit = {
      @annotation.tailrec
      def go(tree: BTree[T], rest: ArrayBuffer[BTree[T]], f: T ⇒ B, n: Long = 0): Unit =
        tree match {
          case BNode(v, l, r @ BNode(_, _, _)) ⇒
            rest += r
            f(v)
            //println(s" rest: ${rest.mkString(",")}")
            go(l, rest, f, n + 1)
          case BNode(v, l, BLeaf) ⇒
            f(v)
            //println(s" rest: ${rest.mkString(",")}")
            go(l, rest, f, n + 1)
          case _ ⇒
            if (rest.size > 0) go(rest.head, rest.tail, f, n)
            else println(n)
        }

      go(self, new ArrayBuffer(1 << 7), f, 0)
    }

    def inOrder[B](f: T ⇒ B): Unit = {
      @annotation.tailrec
      def go(tree: BTree[T], visitStack: List[BTree[T]], delayedStack: List[T], f: T ⇒ B): Unit =
        tree match {
          case BNode(v, BLeaf, r) ⇒
            f(v)
            delayedStack.foreach(f)
            go(r, visitStack, Nil, f)
          case BNode(v, l, r) ⇒
            val (stack0, toPrint0) = r match {
              case n: BNode[T] ⇒ (n :: visitStack, v :: delayedStack)
              case BLeaf       ⇒ (visitStack, v :: delayedStack)
            }
            go(l, stack0, toPrint0, f)
          case _ ⇒
            if (visitStack.size > 0) go(visitStack.head, visitStack.tail, Nil, f)
        }

      self match {
        case BNode(v, l, r) ⇒
          go(l, Nil, Nil, f)
          f(v)
          go(r, Nil, Nil, f)
        case _ ⇒
        //f(null.asInstanceOf[T])
      }
    }

    private def loop(v: T, t: BTree[T]): T = t match {
      case BLeaf ⇒ v
      case BNode(v, left, right) ⇒
        val l = loop(v, left)
        val r = loop(v, right)
        if (ord lt (l, r)) r else l
    }

    def max: T = loop(null.asInstanceOf[T], self)

    def lookup(elem: T): (Option[T], Long) =
      search(elem, self)

    def :+(v: T): BTree[T] = (v, self) match {
      case (value, BLeaf) ⇒
        BNode(value, BLeaf, BLeaf)
      case (candidate, BNode(a, left, right)) if candidate == a ⇒
        BNode(candidate, left, right)
      case (candidate, BNode(a, left, right)) if ord.lt(candidate, a) ⇒
        BNode(a, left :+ candidate, right)
      case (candidate, BNode(a, left, right)) if ord.gt(candidate, a) ⇒
        BNode(a, left, right :+ candidate)
    }
  }

  val size = 500
  val root: BTree[Int] = BNode(size / 2, BLeaf, BLeaf)
  val entries = scala.util.Random.shuffle((1 to size).toList)
  val myTree: BTree[Int] = entries.foldLeft(root)((acc, i) ⇒ acc :+ i)
  entries.foldLeft(0) { (acc, i) ⇒ math.max(acc, myTree.lookup(i)._2.toInt) }

  myTree.foreach(println(_))

  val r: BTree[Int] = BNode(13, BLeaf, BLeaf)
  val t = r :+ 8 :+ 17 :+ 4 :+ 6 :+ 5 :+ 7
  t.foreach(print(_))
  t.preOrder(print(_))
  //t.inOrder(println(_))

  val balancedTree = r :+ 6 :+ 17 :+ 5 :+ 7 :+ 4 :+ 8 :+ 20 :+ 18 :+ 25 :+ 22
  balancedTree.foreach(print(_))
  balancedTree.preOrder(print(_))
  balancedTree.inOrder(d ⇒ print(s"$d,"))

  /*
  BNode(13,
    BNode(8,
      BNode(4, BLeaf,
        BNode(6,
          BNode(5, BLeaf, BLeaf),
          BNode(7, BLeaf, BLeaf))), BLeaf),
    BNode(17, BLeaf, BLeaf))
  */
}
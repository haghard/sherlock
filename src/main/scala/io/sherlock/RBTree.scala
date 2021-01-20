package io.sherlock

import scala.annotation.tailrec

/*
Red-black tree - is a balanced binary search tree
inv 1: No red node has a red child
inv 2: Every path from the root to an empty node contains the same number of black nodes  //to achieve O(log(N))
need to rebalance after any insert
 */

//https://github.com/vkostyukov/scalacaster/blob/master/src/tree/RBTree.scala

/** A color for RB-Tree's nodes.
  */
abstract sealed class Color

case object Red extends Color

case object Black extends Color

/** A Red-Black Tree.
  */
abstract sealed class RBTree[+A <% Ordered[A]] {

  /** The color of this tree.
    */
  def color: Color

  /** The value of this tree.
    */
  def value: A

  /** The left child of this tree.
    */
  def left: RBTree[A]

  /** The right child of this tree.
    */
  def right: RBTree[A]

  /** Checks whether this tree is empty or not.
    */
  def isEmpty: Boolean

  /** Adds the given element into this tree.
    *
    * Time - O(log n)
    * Space - O(log n)
    */
  def :+[B >: A <% Ordered[B]](x: B): RBTree[B] = {
    def balancedAdd(t: RBTree[A]): RBTree[B] =
      if (t.isEmpty) RBTree.single(Red, x)
      else if (x < t.value) balanceLeft(t.color, t.value, balancedAdd(t.left), t.right)
      else if (x > t.value) balanceRight(t.color, t.value, t.left, balancedAdd(t.right))
      else t

    def balanceLeft(c: Color, x: A, l: RBTree[B], r: RBTree[A]) =
      (c, l, r) match {
        case (Black, Branch(Red, y, Branch(Red, z, a, b), c), d) ⇒
          RBTree.single(Red, y, RBTree.single(Black, z, a, b), RBTree.single(Black, x, c, d))
        case (Black, Branch(Red, z, a, Branch(Red, y, b, c)), d) ⇒
          RBTree.single(Red, y, RBTree.single(Black, z, a, b), RBTree.single(Black, x, c, d))
        case _ ⇒ RBTree.single(c, x, l, r)
      }

    def balanceRight(c: Color, x: A, l: RBTree[A], r: RBTree[B]) =
      (c, l, r) match {
        case (Black, a, Branch(Red, y, b, Branch(Red, z, c, d))) ⇒
          RBTree.single(Red, y, RBTree.single(Black, x, a, b), RBTree.single(Black, z, c, d))
        case (Black, a, Branch(Red, z, Branch(Red, y, b, c), d)) ⇒
          RBTree.single(Red, y, RBTree.single(Black, x, a, b), RBTree.single(Black, z, c, d))
        case _ ⇒ RBTree.single(c, x, l, r)
      }

    def blacken(t: RBTree[B]) = RBTree.single(Black, t.value, t.left, t.right)

    blacken(balancedAdd(this))
  }

  def contains[B >: A <% Ordered[B]](elem: B): (Boolean, Long) = {
    @tailrec def loop(elem: B, tree: RBTree[A], n: Long): (Boolean, Long) =
      tree match {
        case Leaf ⇒ (false, n)
        case Branch(_, value, left, right) ⇒
          if (elem < value) loop(elem, left, n + 1L)
          else if (elem > value) loop(elem, right, n + 1L)
          else (true, n)
      }

    loop(elem, this, 0L)
  }

  def height: Int =
    if (isEmpty) 0 else math.max(left.height, right.height) + 1

  /** Fails with given message.
    */
  def fail(m: String) = throw new NoSuchElementException(m)
}

case class Branch[A <% Ordered[A]](color: Color, value: A, left: RBTree[A], right: RBTree[A]) extends RBTree[A] {
  def isEmpty = false
}

case object Leaf extends RBTree[Nothing] {
  def color: Color = Black

  def value: Nothing = fail("An empty tree.")

  def left: RBTree[Nothing] = fail("An empty tree.")

  def right: RBTree[Nothing] = fail("An empty tree.")

  def isEmpty = true
}

object RBTree {

  /** Returns an empty red-black tree instance.
    *
    * Time - O(1)
    * Space - O(1)
    */
  def empty[A]: RBTree[A] = Leaf

  def single[A <% Ordered[A]](c: Color, x: A, l: RBTree[A] = Leaf, r: RBTree[A] = Leaf): RBTree[A] =
    Branch(c, x, l, r)

  /** Creates a new red-black tree from given 'xs' sequence.
    *
    * Time - O(n log n)
    * Space - O(log n)
    */
  def apply[A <% Ordered[A]](xs: Seq[A]): RBTree[A] = {
    var r: RBTree[A] = Leaf
    for (x ← xs) r = r :+ x
    r
  }

  val size    = 500
  val entries = (1 to size).toList
  val tree    = entries.foldLeft(RBTree.single(Black, 0))(_ :+ _)
  entries.foldLeft(0L)((acc, i) ⇒ math.max(acc, tree.contains(i)._2))

  //t.contains(11)

}

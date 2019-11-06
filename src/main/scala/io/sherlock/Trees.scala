package io.sherlock

object Trees {

  //https://javarevisited.blogspot.com/2015/10/how-to-implement-binary-search-tree-in-java-example.html
  //https://javarevisited.blogspot.com/2016/07/binary-tree-preorder-traversal-in-java-using-recursion-iteration-example.html?utm_source=feedburner&utm_medium=email&utm_campaign=Feed:+Javarevisited+(javarevisited)

  case class Tree[T](value: T, children: List[Tree[T]] = Nil)

  def lazyTraverseDF[T](t: Tree[T]): Stream[T] =
    t.value #:: t.children.foldLeft(Stream.empty[T]) { (acc, el) ⇒
      acc #::: lazyTraverseDF(el)
    }

  def traverseWithDepth[T](t: Tree[T], depth: Int = 0): Stream[(T, Int)] =
    (t.value, depth) #:: t.children.foldLeft(Stream.empty[(T, Int)]) { (acc, el) ⇒
      acc #::: traverseWithDepth(el, depth + 1)
    }

  def traverseDF3[A, U](tree: Tree[A])(f: A ⇒ U): Unit = {
    var stack = List[Tree[A]]()
    stack = tree :: stack

    while (stack.nonEmpty) {
      val cur = stack.head
      f(cur.value)

      stack = stack.tail

      if(cur.children.nonEmpty) {
        stack = cur.children ::: stack
      }
    }
  }

  //Depth first - pre-order traversal algorithm
  def traverseDF[A, U](tree: Tree[A])(f: A ⇒ U): Unit = {
    @annotation.tailrec
    def loop(cur: Tree[A], next: List[Tree[A]]): Unit = {
      f(cur.value)
      //println(" - " + predecessors.mkString(","))
      cur.children match {
        case head :: tail ⇒
          loop(head, tail ::: next)
        case Nil ⇒
          if (next.isEmpty) () else loop(next.head, next.tail)
      }
    }

    f(tree.value)
    tree.children.foreach(loop(_, Nil))
  }

  def traverseDF2[A, U](tree: Tree[A])(f: A ⇒ U): Unit = {
    @annotation.tailrec
    def loop(current: Tree[A], next: List[Tree[A]]): Unit = {
      f(current.value)
      //println(" - " + predecessors.mkString(","))
      current.children match {
        case head :: tail ⇒
          loop(head, next ::: tail)
        case Nil ⇒
          if (next.isEmpty) () else loop(next.head, next.tail)
      }
    }

    f(tree.value)
    tree.children.foreach(loop(_, Nil))
  }

  //val t = Tree("root", Tree("a", Tree("a0") ::  Tree("a1") :: Nil) :: Tree("b") :: Tree("c") :: Nil)
  val t =
    Tree(
      "root",
      (Tree("a", Tree("a01") :: Tree("a01", Tree("a11") :: Tree("a12") :: Nil) :: Nil) ::
        Tree("b", Tree("b1") :: Tree("b2") :: Nil) ::
        Tree("c", Tree("c1") :: Tree("c2") :: Tree("c3") :: Nil) :: Nil))

  traverseDF(t)(println(_))
  traverseDF2(t)(println(_))
  traverseDF3(t)(println(_))

  lazyTraverseDF(t) take 25 foreach println

  traverseWithDepth(t) take 25 foreach println

  //Red-black tree - is a balanced binary search tree
  //inv 1: No red node has a red child
  //inv 2: Every path from the root to an empty node contains the same number of black nodes  //to achieve O(log(N))
  //need to rebalance after any insert

  //Similar implementation https://github.com/vkostyukov/scalacaster/blob/master/src/tree/RBTree.scala

  /*
   * Red-Black Tree http://en.wikipedia.org/wiki/Red-black_tree
   *
   * Insert - O(log n)
   * Lookup - O(log n)
   *
   */

  /*
    1. Each node is either red or black.
    2. The root is black. This rule is sometimes omitted. Since the root can always be changed from red to black, but not necessarily vice versa, this rule has little effect on analysis.
    3. All leaves (NIL) are black.
    4. If a node is red, then both its children are black.
    5. Every path from a given node to any of its descendant NIL nodes contains the same number of black nodes.
  */

  sealed trait Color[+A] {
    def elem: A

    def isRed: Boolean

    private def fold[T](fa: A ⇒ T, fb: A ⇒ T): T =
      if (isRed) fa(this.elem) else fb(this.elem)

    def element: A = fold(identity, identity)
  }

  case class Red[+A](elem: A) extends Color[A] {
    override val isRed: Boolean = true
  }

  case class Black[+A](elem: A) extends Color[A] {
    override val isRed: Boolean = false
  }

  trait RBTree[+A]

  case object Leaf extends RBTree[Nothing]

  case class Node[+A](left: RBTree[A], elem: Color[A], right: RBTree[A]) extends RBTree[A]

  def find[A](element: A, tree: RBTree[A], cnt: Long = 0l)(implicit ord: Ordering[A]): (Option[A], Long) = {
    tree match {
      case Leaf ⇒ (None, cnt)
      case Node(left, elem, right) ⇒
        import ord._
        val e = elem.element
        if (element < e) find(element, left, cnt + 1l)
        else if (element > e) find(element, right, cnt + 1l)
        else (Some(e), cnt)
    }
  }

  def contains[A](element: A, tree: RBTree[A], cnt: Long = 0l)(implicit ord: Ordering[A]): (Boolean, Long) = {
    tree match {
      case Leaf ⇒ (false, cnt)
      case Node(left, elem, right) ⇒
        import ord._
        val e = elem.element
        if (element < e) contains(element, left, cnt + 1l)
        else if (element > e) contains(element, right, cnt + 1l)
        else (true, cnt)
    }
  }

  def +[A](element: A, tree: RBTree[A])(implicit ord: Ordering[A]): RBTree[A] =
    insert(element, tree)

  def insert[A](element: A, tree: RBTree[A])(implicit ord: Ordering[A]): RBTree[A] = {
    def ins(tree: RBTree[A])(implicit ord: Ordering[A]): RBTree[A] = {
      import ord._
      tree match {
        case Leaf ⇒
          Node(Leaf, Red(element), Leaf)
        case Node(left, elem, right) ⇒
          val e = elem.element
          if (element < e) balance(ins(left), elem, right)
          else if (element > e) balance(left, elem, ins(right))
          else tree
      }
    }

    // result is always the same
    def balance(left: RBTree[A], c: Color[A], right: RBTree[A]): RBTree[A] = {
      (left, c, right) match {
        case (Node(Node(a, Red(x), b), Red(y), c), Black(z), d) ⇒
          Node(Node(a, Black(x), b), Red(y), Node(c, Black(z), d))
        case (Node(a, Red(x), Node(b, Red(y), c)), Black(z), d) ⇒
          Node(Node(a, Black(x), b), Red(y), Node(c, Black(z), d))
        case (a, Black(x), Node(Node(b, Red(y), c), Red(z), d)) ⇒
          Node(Node(a, Black(x), b), Red(y), Node(c, Black(z), d))
        case (a, Black(x), Node(b, Red(y), Node(c, Red(z), d))) ⇒
          Node(Node(a, Black(x), b), Red(y), Node(c, Black(z), d))
        case _ ⇒ Node(left, c, right)
      }
    }

    ins(tree)
  }

  //this tree doesn't work, smth is wrong with balancing. USE io.sherlock.RBTree

  val size = 500
  val root: RBTree[Int] = Node(Leaf, Black(0), Leaf)
  val entries = scala.util.Random.shuffle((1 to size).toVector)
  val myTree = entries.foldLeft(root)((acc, i) ⇒ insert(i, acc))
  entries.foldLeft(0) { (acc, i) ⇒ math.max(acc, contains(i, myTree)._2.toInt) }
  entries.foldLeft(0) { (acc, i) ⇒ if (acc == 0) contains(i, myTree)._2.toInt else math.min(acc, contains(i, myTree)._2.toInt) }

  import com.abahgat.suffixtree.GeneralizedSuffixTree

  val suffixTree = new GeneralizedSuffixTree()

  suffixTree.put("cacao", 0)
  suffixTree.put("chocolate", 1)

  suffixTree.put("11as222", 3)
  suffixTree.put("11222as", 4)

  suffixTree.search("as")

  println("Searching: " + suffixTree.search("cac", 2))
  println("Searching: " + suffixTree.search("caco"))

  //A Buffer implementation backed by a list. It provides constant time prepend and append.
  // Most other operations are linear.
  val lb = scala.collection.mutable.ListBuffer[Int]()
  lb += 1 //append
  lb.+=:(90) //prepend
  lb(2) //random access  linear

  //An implementation of the Buffer class using an array to represent the assembled sequence internally.
  // Append, update and random access take constant time (amortized time). Prepends and removes are linear in the buffer size.
  val ab = new scala.collection.mutable.ArrayBuffer[Int](10)
  ab.+=(1) //append

  ab.+=:(2) //prepend - linear


  val ml0 = scala.collection.mutable.MutableList[Int]()
  //prepend O(1)
  ml0.+=:(1)
  ml0.+=:(2)


  val ml1 = scala.collection.mutable.MutableList[Int]()
  //append O(1)
  ml1.+=(1)
  ml1.+=(2)

  //collection.mutable.Buffer[Int]()
  val mb0 = collection.mutable.ArrayBuffer[Int]()
  //prepend O(n)
  mb0.+=:(1)
  mb0.+=:(2)
  mb0

  val mb1 = collection.mutable.ArrayBuffer[Int]()
  //append O(1)
  mb1.+=(1)
  mb1.+=(2)
  mb1


  

  //suffixTree.search()
}
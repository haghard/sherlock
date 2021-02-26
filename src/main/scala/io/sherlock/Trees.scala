package io.sherlock

import org.isarnproject.collections.mixmaps.nearest.NearestMap
import org.isarnproject.collections.mixmaps.ordered.OrderedSet
import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, Json}

import java.net.InetAddress
import scala.collection.Map

object Trees {

  //https://javarevisited.blogspot.com/2015/10/how-to-implement-binary-search-tree-in-java-example.html
  //https://javarevisited.blogspot.com/2016/07/binary-tree-preorder-traversal-in-java-using-recursion-iteration-example.html?utm_source=feedburner&utm_medium=email&utm_campaign=Feed:+Javarevisited+(javarevisited)

  //https://github.com/politrons/reactiveScala/blob/master/scala_features/src/main/scala/app/impl/algorithms/TreeDS.scala

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

      if (cur.children.nonEmpty)
        stack = cur.children ::: stack
    }
  }

  def outK(k: String): Unit =
    print(s""""$k":""")

  def outK0(k: String): Unit =
    print(s"""{"$k":""")

  def outV(cur: JsValue): Unit =
    cur match {
      case JsString(value) ⇒
        print(s""""$value",""")
      case JsBoolean(b) ⇒
        print(s"""$b,""")
      case JsNumber(n) ⇒
        print(s"""$n,""")
      case JsObject(_) ⇒
        print(s"""{""")
    }

  val jsValue0 = JsObject(
    Map(
      "a" → JsObject(Map("a1" → JsObject(Map("a11" → JsNumber(1), "a12" → JsNumber(2))))),
      "b" → JsObject(Map("b1" → JsObject(Map("b11" → JsNumber(3), "b12" → JsNumber(4))))),
      "c" → JsObject(Map("c1" → JsObject(Map("c11" → JsNumber(5)))))
    )
  )

  val jsValue1 = JsObject(
    Map(
      "a" → JsObject(Map("a1" → JsNumber(1), "a2" → JsNumber(2))),
      "b" → JsObject(Map("b1" → JsNumber(3))),
      "c" → JsObject(Map("c1" → JsNumber(4)))
    )
  )

  val jsValue3 = JsObject(Map("a1" → JsNumber(1), "a2" → JsNumber(2)))

  val jsValue2 = JsObject(
    Map(
      "a" → JsObject(Map("a1" → JsNumber(1))),
      "b" → JsObject(Map("b1" → JsObject(Map("b11" → JsString("1"), "b12" → JsNumber(2))))),
      "c" → JsObject(Map("c1" → JsObject(Map("c11" → JsString("1")))))
    )
  )

  val jsValue = JsObject(
    Map(
      "a" → JsObject(Map("a1" → JsObject(Map("a11" → JsNumber(1), "a12" → JsString("2"))))),
      "b" → JsObject(Map("b1" → JsObject(Map("b11" → JsString("1"), "b12" → JsObject(Map("b21" → JsNumber(2))))))),
      "c" → JsObject(Map("c1" → JsObject(Map("c11" → JsString("1")))))
    )
  )

  Json.stringify(jsValue)
  new String(Json.toBytes(jsValue))
  //Json.parse()

  import com.github.plokhotnyuk.jsoniter_scala.core._

  implicit val jCodec = new JsonValueCodec[JsObject] {

    override def decodeValue(in: JsonReader, default: JsObject): JsObject = ???

    def outVal(cur: JsValue, out: JsonWriter): Unit =
      cur match {
        case JsString(value) ⇒
          out.writeVal(value)
        case JsBoolean(b) ⇒
          out.writeVal(b)
        case JsNumber(n) ⇒
          out.writeVal(n)
        case JsObject(_) ⇒
          out.writeObjectStart()
      }

    //http://www.lihaoyi.com/post/ZeroOverheadTreeProcessingwiththeVisitorPattern.html#tree-construction-visitors
    //https://blog.leifbattermann.de/2017/10/08/error-and-state-handling-with-monad-transformers-in-scala/
    def loop(js: JsValue, out: JsonWriter, stack: List[Map[String, JsValue]], keys: List[String]): Unit =
      js match {
        case JsObject(kvs) if kvs.nonEmpty ⇒
          val k    = kvs.keysIterator.next
          val js   = kvs(k)
          val rest = kvs - k
          out.writeKey(k)
          if (rest.nonEmpty) loop(js, out, rest :: stack, keys)
          else loop(js, out, stack, keys.tail)
        case v ⇒
          outVal(v, out)
          if (stack.isEmpty) ()
          else {
            val map = stack.head
            if (map.nonEmpty) {
              val k  = map.keysIterator.next
              val js = map(k)
              out.writeKey(k)
              val rest = map - k
              if (rest.isEmpty) loop(js, out, stack.tail, keys)
              else loop(js, out, List(rest), keys)
            }
          }
      }

    def go(js: JsObject, out: JsonWriter): Unit = {
      val kvs  = js.value
      val keys = kvs.keysIterator.toList
      val k    = keys.head
      val v    = kvs(k)
      out.writeObjectStart()
      out.writeKey(k)
      loop(v, out, List(kvs - k), k :: Nil)
      out.writeObjectEnd()
    }

    override def encodeValue(js: JsObject, out: JsonWriter): Unit =
      go(js, out)

    override val nullValue: JsObject = null
  }

  def serialize[T: JsonValueCodec](v: T) =
    writeToArray(v)

  def traverseADT[U](tree: JsObject): Unit = {

    def loop(js: JsValue, stack: List[Map[String, JsValue]]): Unit =
      js match {
        case JsObject(kvs) if kvs.nonEmpty ⇒
          val k    = kvs.keysIterator.next
          val js   = kvs(k)
          val rest = kvs - k
          outK0(k)
          if (rest.nonEmpty) loop(js, rest :: stack)
          else loop(js, stack)
        case v ⇒
          outV(v)
          if (stack.isEmpty) ()
          else {
            val obj = stack.head
            if (obj.nonEmpty) {
              val k  = obj.keysIterator.next
              val js = obj(k)
              outK(k)
              val rest = obj - k
              if (rest.isEmpty) loop(js, stack.tail)
              else loop(js, List(rest))
            } //else print("Boom!!!")
          }
      }

    val kvs = tree.value
    val k   = kvs.keysIterator.next
    val v   = kvs(k)
    print("{")
    outK(k)

    loop(v, List(kvs - k))
    print("}")
  }

  //Depth first - pre-order traversal algorithm
  def traverseDF[A, U](tree: Tree[A])(f: A ⇒ U): Unit = {
    @annotation.tailrec
    def loop(cur: Tree[A], visitNext: List[Tree[A]]): Unit = {
      f(cur.value)
      println(
        s"${cur.value} - chs:[${cur.children.map(_.value).mkString(",")}] - stack:[${visitNext.map(_.value).mkString(",")}]"
      )
      cur.children match {
        case head :: tail ⇒
          loop(head, tail ::: visitNext)
        case Nil ⇒
          if (visitNext.isEmpty) () else loop(visitNext.head, visitNext.tail)
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
      Tree(
        "a",
        Tree("a1", Tree("a11") :: Tree("a12") :: Nil) :: Tree("a2", Tree("a21") :: Tree("a22") :: Nil) :: Nil
      ) ::
      Tree("b", Tree("b1") :: Tree("b2") :: Nil) ::
      Tree("c", Tree("c1") :: Tree("c2") :: Tree("c3") :: Nil) :: Nil
    )

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

  final case class Red[+A](elem: A) extends Color[A] {
    override val isRed: Boolean = true
  }

  final case class Black[+A](elem: A) extends Color[A] {
    override val isRed: Boolean = false
  }

  trait RBTree[+A]

  case object Leaf extends RBTree[Nothing]

  final case class Node[+A](left: RBTree[A], elem: Color[A], right: RBTree[A]) extends RBTree[A]

  def find[A](element: A, tree: RBTree[A], cnt: Long = 0L)(implicit ord: Ordering[A]): (Option[A], Long) =
    tree match {
      case Leaf ⇒ (None, cnt)
      case Node(left, elem, right) ⇒
        import ord._
        val e = elem.element
        if (element < e) find(element, left, cnt + 1L)
        else if (element > e) find(element, right, cnt + 1L)
        else (Some(e), cnt)
    }

  def contains[A](element: A, tree: RBTree[A], cnt: Long = 0L)(implicit ord: Ordering[A]): (Boolean, Long) =
    tree match {
      case Leaf ⇒ (false, cnt)
      case Node(left, elem, right) ⇒
        import ord._
        val e = elem.element
        if (element < e) contains(element, left, cnt + 1L)
        else if (element > e) contains(element, right, cnt + 1L)
        else (true, cnt)
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
    def balance(left: RBTree[A], c: Color[A], right: RBTree[A]): RBTree[A] =
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

    ins(tree)
  }

  //this tree doesn't work, smth's wrong with balancing. USE io.sherlock.RBTree or OrderedSet

  val size              = 500
  val root: RBTree[Int] = Node(Leaf, Black(0), Leaf)
  val entries           = scala.util.Random.shuffle((1 to size).toVector)
  val myTree            = entries.foldLeft(root)((acc, i) ⇒ insert(i, acc))
  entries.foldLeft(0)((acc, i) ⇒ math.max(acc, contains(i, myTree)._2.toInt))
  entries.foldLeft(0) { (acc, i) ⇒
    if (acc == 0) contains(i, myTree)._2.toInt else math.min(acc, contains(i, myTree)._2.toInt)
  }

  //

  import org.isarnproject.collections.mixmaps.ordered._
  val tree     = OrderedSet.key[Int]
  val shuffled = scala.util.Random.shuffle(Vector.tabulate(50)(identity))
  val dataTree = shuffled.foldLeft(tree)((tree, c) ⇒ tree + c)

  dataTree.nodes
  dataTree.node(35)
  shuffled.foldLeft(true)((acc, i) ⇒ acc && dataTree.contains(i))
  dataTree.nodesFrom(25)
  shuffled.foldLeft(true)((acc, i) ⇒ acc && dataTree.getNode(i).isDefined)

  //import org.isarnproject.collections.mixmaps.nearest._

  implicit val ipV4Address = new Numeric[String] {
    val size                                        = 4
    override def plus(x: String, y: String): String = ???

    override def toDouble(x: String): Double = ???

    override def toFloat(x: String): Float = ???

    override def toInt(x: String): Int = ???

    override def negate(x: String): String = ???

    override def fromInt(x: Int): String = ???

    override def toLong(x: String): Long = ???

    override def times(x: String, y: String): String = ???

    override def minus(x: String, y: String): String = ???

    override def compare(x: String, y: String): Int = {
      val xOctects = InetAddress.getByName(x).getHostAddress.toCharArray
      val yOctects = InetAddress.getByName(y).getHostAddress.toCharArray
      require((xOctects.size == yOctects.size) && (yOctects.size == size), "Address should match ipv4 schema")

      def divergedIndex(a: Array[Int], b: Array[Int]): Option[Int] = {
        @scala.annotation.tailrec
        def loop(start: Int, end: Int): Option[Int] = {
          val mid = start + (end - start) / 2
          println("look at " + mid)
          if (start > end) None
          else if (a(mid) == b(mid)) loop(mid + 1, end)
          else Some(mid)
        }
        if (a.size >= 1 && b.size >= 1 && a(0) != b(0)) Some(0)
        else loop(0, math.min(a.length, b.length) - 1)
      }

      def intersect(a: Array[Int], b: Array[Int]): Int = {
        var i_a = 0; var i_b = 0
        //new scala.collection.mutable.ArrayBuffer[Int]()
        val result        = new scala.collection.mutable.ListBuffer[Int]()
        var divergedIndex = 0
        while (i_a < a.size && i_b < b.size)
          if (a(i_a) < b(i_b)) {
            i_a += 1
          } else if (b(i_b) < a(i_a)) {
            i_b += 1
          } else {
            result += a(i_a)
            divergedIndex += 1
            i_a += 1
            i_b += 1
          }
        divergedIndex
      }

      def tryCompary(x: Array[Char], y: Array[Char], i: Int = 0): Int = {
        val a = x(i).toInt
        val b = y(i).toInt
        if (i < size)
          if (a < b) -1 else if (a > b) 1 else tryCompary(x, y, i + 1)
        else 0
      }

      tryCompary(xOctects, yOctects, 0)
    }
  }

  val zero: NearestMap[String, Int] = NearestMap.key[String].value[Int].empty
  val ips = zero + ("127.0.0.1" → 0) + ("127.0.0.2" → 1) + ("127.0.0.3" → 2) + ("127.0.0.4" → 3) +
    ("127.0.0.5" → 4) + ("127.0.0.6" → 5) + ("127.0.0.7" → 6)

  ips.nearest("127.0.0.3")

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
  lb += 1    //append - O(1)
  lb.+=:(90) //prepend - O(1)
  lb(1)      //random access - O(n)

  //An implementation of the Buffer class using an array to represent the assembled sequence internally.
  // Append, update and random access take constant time (amortized time). Prepends and removes are linear in the buffer size.
  val ab = new scala.collection.mutable.ArrayBuffer[Int](10)
  ab.+=(1) //append

  ab.+=:(2) //prepend - linear

  val ml0 = scala.collection.mutable.MutableList[Int]()
  //prepend - O(1)
  ml0.+=:(1)
  ml0.+=:(2)

  val ml1 = scala.collection.mutable.MutableList[Int]()
  //append - O(1)
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

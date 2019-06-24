package scalan.util

import scalan.RType
import scalan.RType.PairType

import scala.collection.{GenIterable, Seq, mutable}
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.collection.generic.CanBuildFrom
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object CollectionUtil {

  def concatArrays[T](xs: Array[T], ys: Array[T]): Array[T] = {
    val len = xs.length + ys.length
    val result = (xs match {
      case arr: Array[AnyRef] => new Array[AnyRef](len)
      case arr: Array[Byte] => new Array[Byte](len)
      case arr: Array[Short] => new Array[Short](len)
      case arr: Array[Int] => new Array[Int](len)
      case arr: Array[Long] => new Array[Long](len)
      case arr: Array[Char] => new Array[Char](len)
      case arr: Array[Float] => new Array[Float](len)
      case arr: Array[Double] => new Array[Double](len)
      case arr: Array[Boolean] => new Array[Boolean](len)
    }).asInstanceOf[Array[T]]
    Array.copy(xs, 0, result, 0, xs.length)
    Array.copy(ys, 0, result, xs.length, ys.length)
    result
  }

//  def newUnboxedArray[T](len: Int)(implicit cT: Class[T]): Array[T] = (cT match {
//    case ClassTag.Byte => new Array[Int](len)
//    case ClassTag.Short => new Array[Short](len)
//    case ClassTag.Char => new Array[Char](len)
//    case ClassTag.Int => new Array[Int](len)
//    case ClassTag.Long => new Array[Long](len)
//    case ClassTag.Float => new Array[Float](len)
//    case ClassTag.Double => new Array[Double](len)
//    case ClassTag.Boolean => new Array[Boolean](len)
//    case ClassTag.Any => new Array[Any](len)
//    case ClassTag.AnyVal => new Array[AnyVal](len)
//    case ClassTag.AnyRef => new Array[AnyRef](len)
//  }).asInstanceOf[Array[T]]


  def hashElement[@specialized T](element: T): Int = element match {
    case arrayVal: Array[_] => arrayHashCode(arrayVal)
    case _ => {
      if (PrimitiveTypeHashUtil.isPrimitiveType(element))
        PrimitiveTypeHashUtil.hashPrimitive(element)
      else element.hashCode()
    }
  }

  def arrayHashCode[@specialized T](array: Array[T]): Int = if (array == null) 0
  else {
    if (array.isInstanceOf[Array[Tuple2[_, _]]]) {
      return deepPairedArrayHashCode(array.asInstanceOf[Array[Tuple2[_, _]]])
    }
    array match {
      case arr: Array[AnyRef] => deepArrayHashCode(arr)
      case arr: Array[Byte] => byteArrayHashCode(arr)
      case arr: Array[Short] => shortArrayHashCode(arr)
      case arr: Array[Int] => intArrayHashCode(arr)
      case arr: Array[Char] => charArrayHashCode(arr)
      case arr: Array[Long] => longArrayHashCode(arr)
      case arr: Array[Float] => floatArrayHashCode(arr)
      case arr: Array[Double] => doubleArrayHashCode(arr)
      case arr: Array[Boolean] => boolArrayHashCode(arr)
    }
}

  def hashOne[@specialized T](element: T): Int = {
    var elementHash = 0
    if (element == null) elementHash = 0
    else {
      elementHash = hashElement(element)
    }
    elementHash
  }

  private def deepPairedArrayHashCode(array: Array[Tuple2[_, _]]): Int = if (array == null) 0
  else {
    val length = array.length
    var i = 0
    var lHash = 1
    var rHash = 1
    while (i < length) {
      val element = array(i)
      lHash = 31 * lHash + hashOne(element._1)
      rHash = 31 * rHash + hashOne(element._2)
      i += 1
    }

    41 * lHash + rHash
  }

  private def calcPrimitiveArrayHashCode[@specialized T](arr: Array[T], hashT: T => Int): Int = {
    if (arr == null) return 0
    var hash: Int = 1
    val length = arr.length
    var i: Int = 0
    while (i < length) {
      val element = arr(i)
      hash = 31 * hash + hashT(element)

      i += 1
    }
    return hash
  }

  private def deepArrayHashCode(arr: Array[AnyRef]): Int = {
    calcPrimitiveArrayHashCode(arr, hashOne)
  }

  private def byteArrayHashCode(arr: Array[Byte]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashByte)
  }

  private def shortArrayHashCode(arr: Array[Short]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashShort)
  }

  private def intArrayHashCode(arr: Array[Int]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashInt)
  }

  private def charArrayHashCode(arr: Array[Char]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashChar)
  }

  private def longArrayHashCode(arr: Array[Long]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashLong)
  }

  private def floatArrayHashCode(arr: Array[Float]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashFloat)
  }

  private def doubleArrayHashCode(arr: Array[Double]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashDouble)
  }

  private def boolArrayHashCode(arr: Array[Boolean]): Int = {
    calcPrimitiveArrayHashCode(arr, PrimitiveTypeHashUtil.hashBool)
  }

  def foldRight[A,B](xs: Seq[A])(proj: A => B)(f: (A,B) => B): B =
    xs.foldRight[B](null.asInstanceOf[B]) { case (a, b) =>
      b match {
        case null => proj(a)
        case _ => f(a, b)
      }
    }

  def createMultiMap[K,V](kvs: GenIterable[(K,V)]): Map[K, ArrayBuffer[V]] = {
    val res = HashMap.empty[K, ArrayBuffer[V]]
    kvs.foreach { case (k, v) =>
      if (res.contains(k))
        res(k) += v
      else
        res += k -> ArrayBuffer(v)
      ()
    }
    res.toMap
  }

  def joinSeqs[O, I, K](outer: GenIterable[O], inner: GenIterable[I])(outKey: O=>K, inKey: I=>K): GenIterable[(O,I)] = {
    val kvs = createMultiMap(inner.map(i => (inKey(i), i)))
    val res = outer.flatMap(o => {
      val ko = outKey(o)
      kvs(ko).map(i => (o,i))
    })
    res
  }

  def outerJoinSeqs[O, I, K, R]
      (outer: Seq[O], inner: Seq[I])
      (outKey: O=>K, inKey: I=>K)
      (projO: (K,O) => R, projI: (K,I) => R, proj:(K,O,I) => R): Seq[(K,R)] = {
    val res = ArrayBuffer.empty[(K,R)]
    val kis = inner.map(i => (inKey(i), i))
    val kvs = createMultiMap(kis)
    val outerKeys = mutable.Set.empty[K]
    for (o <- outer) {
      val ko = outKey(o)
      outerKeys += ko
      if (!kvs.contains(ko))
        res += ((ko, projO(ko, o)))
      else
        for (i <- kvs(ko))
          res += ((ko, proj(ko, o, i)))
    }
    for ((k,i) <- kis if !outerKeys.contains(k))
      res += ((k, projI(k, i)))
    res
  }

  def outerJoin[K, L, R, O]
        (left: Map[K, L], right: Map[K, R])
        (l: (K,L) => O, r: (K,R) => O, inner: (K,L,R) => O): Map[K,O] = {
    val res = HashMap.empty[K, O]
    val lks = left.keySet
    val rks = right.keySet
    val leftOnly = lks diff rks
    val rightOnly = rks diff lks
    val both = lks intersect rks
    for (lk <- leftOnly)
      res += lk -> l(lk, left(lk))
    for (rk <- rightOnly)
      res += rk -> r(rk, right(rk))
    for (k <- both)
      res += k -> inner(k, left(k), right(k))
    res.toMap
  }

  def join[K,V,R](ks: List[K], kv: Map[K,V])(f: (K,V) => R): List[R] = {
    val vs = ks.map(k => kv.get(k) match {
      case Some(v) => v
      case None => sys.error(s"Cannot find value for key $k")
    })
    (ks zip vs).map(f.tupled)
  }

  def unboxedArray[T:ClassTag](in: Seq[T]): Array[T] = {
    in.toArray[T]
    //TODO optimize intermediate allocation
//    implicit val tagT = ClassTag.Any.asInstanceOf[ClassTag[T]]
//    if (in.isEmpty)
//      in.toArray
//    else
//      (in.head match {
//        case _: java.lang.Byte => in.map(_.asInstanceOf[Byte]).toArray
//        case _: java.lang.Short => in.map(_.asInstanceOf[Short]).toArray
//        case _: java.lang.Integer => in.map(_.asInstanceOf[Int]).toArray
//        case _: java.lang.Long => in.map(_.asInstanceOf[Long]).toArray
//        case _: java.lang.Double => in.map(_.asInstanceOf[Double]).toArray
//        case _: java.lang.Float => in.map(_.asInstanceOf[Float]).toArray
//        case _: java.lang.Boolean => in.map(_.asInstanceOf[Boolean]).toArray
//        case _: java.lang.Character => in.map(_.asInstanceOf[Char]).toArray
//        case _: java.lang.String => in.map(_.asInstanceOf[String]).toArray
//        case _ => in.toArray
//      }).asInstanceOf[Array[T]]
  }

  implicit class AnyOps[A](x: A) {
    def zipWithExpandedBy[B](f: A => List[B]): List[(A,B)] = {
      val ys = f(x)
      List.fill(ys.length)(x) zip ys
    }
    def traverseDepthFirst(f: A => List[A]): List[A] = {
      var all: List[A] = Nil
      var stack = List(x)
      while (stack.nonEmpty) {
        val h = stack.head
        stack = stack.tail

        var next = f(h).reverse
        while (next.nonEmpty) {
          stack = next.head :: stack
          next = next.tail
        }
        all = h :: all
      }
      all.reverse
    }
  }
  
  implicit class AnyRefOps[A <: AnyRef](x: A) {
    def transformConserve(f: A => A) = {
      val newX = f(x)
      if (newX eq x) x else newX
    }
  }

  implicit class OptionOps[A](source: Option[A]) {
    def mergeWith[K](other: Option[A], merge: (A,A) => A): Option[A] = (source, other) match {
      case (_, None) => source
      case (None, Some(_)) => other
      case (Some(x), Some(y)) => Some(merge(x, y))
    }
  }
  implicit class OptionOfAnyRefOps[A <: AnyRef](source: Option[A]) {
    def mapConserve[B <: AnyRef](f: A => B): Option[B] = source match {
      case Some(a) =>
        val b = f(a)
        if (b.eq(a)) source.asInstanceOf[Option[B]]
        else Some(b)
      case None => None
    }
  }

  implicit class ListOps[A](source: List[A]) {
  }

  implicit class HashMapOps[K,V](source: java.util.HashMap[K,V]) {
    def toImmutableMap: Map[K,V] = {
      var res = Map[K,V]()
      source.forEach((t: K, u: V) => res = res + (t -> u))
      res
    }
  }

  implicit class TraversableOps[A, Source[X] <: GenIterable[X]](xs: Source[A]) {

    def filterCast[B:ClassTag](implicit cbf: CanBuildFrom[Source[A], B, Source[B]]): Source[B] = {
      val b = cbf()
      for (x <- xs) {
        x match {
          case y: B =>
            b += y
          case _ =>
        }
      }
      b.result()
    }

    def cast[B:ClassTag](implicit cbf: CanBuildFrom[Source[A], B, Source[B]]): Source[B] = {
      for (x <- xs) {
        assert(x match { case _: B => true case _ => false}, s"Value $x doesn't conform to type ${reflect.classTag[B]}")
      }
      xs.asInstanceOf[Source[B]]
    }

    /** Applies 'f' to elements of 'xs' until 'f' returns Some(b),
      * which is immediately returned as result of this method.
      * If not such element found, returns None as result. */
    def findMap[B](f: A => Option[B]): Option[B] = {
      for (x <- xs) {
        val y = f(x)
        if (y.isDefined) return y
      }
      return None
    }

    def filterMap[B](f: A => Option[B])(implicit cbf: CanBuildFrom[Source[A], B, Source[B]]): Source[B] = {
       val b = cbf()
       for (x <- xs) {
         f(x) match {
           case Some(y) =>
             b += y
           case None =>
         }
       }
       b.result()
    }

    def mapUnzip[B1, B2](f: A => (B1,B2))
        (implicit cbf1: CanBuildFrom[Source[A], B1, Source[B1]],
            cbf2: CanBuildFrom[Source[A], B2, Source[B2]]): (Source[B1], Source[B2]) =
    {
      val b1 = cbf1()
      val b2 = cbf2()
      for (x <- xs) {
        val (y1, y2) = f(x)
        b1 += y1; b2 += y2
      }
      (b1.result(), b2.result())
    }

    def mapUnzip[B1, B2, B3](f: A => (B1,B2,B3))
        (implicit cbf1: CanBuildFrom[Source[A], B1, Source[B1]],
            cbf2: CanBuildFrom[Source[A], B2, Source[B2]],
            cbf3: CanBuildFrom[Source[A], B3, Source[B3]]): (Source[B1], Source[B2], Source[B3]) =
    {
      val b1 = cbf1()
      val b2 = cbf2()
      val b3 = cbf3()
      for (x <- xs) {
        val (y1, y2, y3) = f(x)
        b1 += y1; b2 += y2; b3 += y3
      }
      (b1.result(), b2.result(), b3.result())
    }

    def distinctBy[K](key: A => K)(implicit cbf: CanBuildFrom[Source[A], A, Source[A]]): Source[A] = {
      val keys = mutable.Set[K]()
      val b = cbf()
      for (x <- xs) {
        val k = key(x)
        if (!keys.contains(k)) {
          b += x
          keys += k
        }
      }
      b.result()
    }

    /** Apply m for each element of this collection, group by key and reduce each group using r.
      * @returns one item for each group in a new collection of (K,V) pairs. */
    def mapReduce[K, V](map: A => (K, V))(reduce: (V, V) => V)
                       (implicit cbf: CanBuildFrom[Source[A], (K,V), Source[(K,V)]]): Source[(K, V)] = {
      val result = scala.collection.mutable.LinkedHashMap.empty[K, V]
      xs.foldLeft(result)((r, x) => {
        val (key, value) = map(x)
        result.update(key, if (result.contains(key)) reduce(result(key), value) else value)
        result
      })
      val b = cbf()
      for (kv <- result) b += kv
      b.result()
    }

    def mergeWith[K]
          (ys: Source[A], key: A => K, merge: (A,A) => A)
          (implicit cbf: CanBuildFrom[Source[A], A, Source[A]]): Source[A] = {
      val b = cbf()
      for (v <- (xs ++ ys).mapReduce(x => (key(x), x))(merge))
        b += v._2
      b.result()
    }

    def partitionByType[B <: A, C <: A]
        (implicit tB: ClassTag[B],
                  cbB: CanBuildFrom[Source[A], B, Source[B]],
                  cbC: CanBuildFrom[Source[A], C, Source[C]]): (Source[B], Source[C]) = {
      val bs = cbB()
      val cs = cbC()
      for (x <- xs)
        if (tB.runtimeClass.isAssignableFrom(x.getClass))
          bs += x.asInstanceOf[B]
        else
          cs += x.asInstanceOf[C]
      (bs.result(), cs.result())
    }

    private def flattenIter(i: Iterator[_]): Iterator[_] = i.flatMap(x => x match {
      case nested: GenIterable[_] => nested
      case arr: Array[_] => arr.iterator
      case _ => Iterator.single(x)
    })

    def sameElements2[B >: A](that: GenIterable[B]): Boolean = {
      val i1: Iterator[Any] = flattenIter(xs.iterator)
      val i2: Iterator[Any] = flattenIter(that.iterator)
      i1.sameElements(i2)
    }

    def deepHashCode: Int = {
      var result: Int = 1
      for (x <- xs) {
        val elementHash = x match {
          case arr: Array[_] => arrayHashCode(arr)
          case _ => x.hashCode()
        }
        result = 31 * result + elementHash;
      }
      result
    }
  }

  private def sameLengthErrorMsg[A,B](xs: Seq[A], ys: Seq[B]) =
    s"Collections should have same length but was ${xs.length} and ${ys.length}:\n xs=$xs;\n ys=$ys"

  def assertSameLength[A,B](xs: Seq[A], ys: Seq[B]) = {
    assert(xs.length == ys.length, sameLengthErrorMsg(xs, ys))
  }

  def requireSameLength[A,B](xs: Seq[A], ys: Seq[B]) = {
    require(xs.length == ys.length, sameLengthErrorMsg(xs, ys))
  }
}



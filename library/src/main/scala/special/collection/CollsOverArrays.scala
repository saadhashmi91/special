package special.collection {
  import scalan._

  trait CollsOverArrays extends Base { self: Library =>
    import CReplColl._;
    import Coll._;
    import CollBuilder._;
    import CollOverArray._;
    import CollOverArrayBuilder._;
    import Monoid._;
    import MonoidBuilder._;
    import MonoidBuilderInst._;
    import PairColl._;
    import PairOfCols._;
    import ReplColl._;
    import WArray._;
    abstract class CollOverArray[A](val toArray: Rep[WArray[A]]) extends Coll[A] {
      def builder: Rep[CollBuilder] = RCollOverArrayBuilder();
      def length: Rep[Int] = CollOverArray.this.toArray.length;
      def apply(i: Rep[Int]): Rep[A] = CollOverArray.this.toArray.apply(i);
      @NeverInline override def isEmpty: Rep[Boolean] = delayInvoke;
      @NeverInline override def nonEmpty: Rep[Boolean] = delayInvoke;
      @NeverInline override def isDefinedAt(idx: Rep[Int]): Rep[Boolean] = delayInvoke;
      @NeverInline def getOrElse(i: Rep[Int], default: Rep[A]): Rep[A] = delayInvoke;
      @NeverInline def map[B](f: Rep[scala.Function1[A, B]]): Rep[Coll[B]] = delayInvoke;
      def foreach(f: Rep[scala.Function1[A, Unit]]): Rep[Unit] = CollOverArray.this.toArray.foreach(f);
      def exists(p: Rep[scala.Function1[A, Boolean]]): Rep[Boolean] = CollOverArray.this.toArray.exists(p);
      def forall(p: Rep[scala.Function1[A, Boolean]]): Rep[Boolean] = CollOverArray.this.toArray.forall(p);
      def filter(p: Rep[scala.Function1[A, Boolean]]): Rep[Coll[A]] = CollOverArray.this.builder.fromArray[A](CollOverArray.this.toArray.filter(p));
      @NeverInline def foldLeft[B](zero: Rep[B], op: Rep[scala.Function1[scala.Tuple2[B, A], B]]): Rep[B] = delayInvoke;
      def slice(from: Rep[Int], until: Rep[Int]): Rep[Coll[A]] = CollOverArray.this.builder.fromArray[A](CollOverArray.this.toArray.slice(from, until));
      def sum(m: Rep[Monoid[A]]): Rep[A] = CollOverArray.this.toArray.foldLeft(m.zero, fun(((in: Rep[scala.Tuple2[A, A]]) => {
        val b: Rep[A] = in._1;
        val a: Rep[A] = in._2;
        m.plus(b, a)
      })));
      def zip[B](ys: Rep[Coll[B]]): Rep[PairColl[A, B]] = CollOverArray.this.builder.pairColl[A, B](this, ys);
      @NeverInline def append(other: Rep[Coll[A]]): Rep[Coll[A]] = delayInvoke;
      @NeverInline def reverse: Rep[Coll[A]] = delayInvoke;
      @NeverInline def indices: Rep[Coll[Int]] = delayInvoke;
      @NeverInline override def flatMap[B](f: Rep[scala.Function1[A, Coll[B]]]): Rep[Coll[B]] = delayInvoke;
      @NeverInline override def segmentLength(p: Rep[scala.Function1[A, Boolean]], from: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def indexWhere(p: Rep[scala.Function1[A, Boolean]], from: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def lastIndexWhere(p: Rep[scala.Function1[A, Boolean]], end: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def take(n: Rep[Int]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def partition(pred: Rep[scala.Function1[A, Boolean]]): Rep[scala.Tuple2[Coll[A], Coll[A]]] = delayInvoke;
      @NeverInline override def patch(from: Rep[Int], patch: Rep[Coll[A]], replaced: Rep[Int]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def updated(index: Rep[Int], elem: Rep[A]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def updateMany(indexes: Rep[Coll[Int]], values: Rep[Coll[A]]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def mapReduce[K, V](m: Rep[scala.Function1[A, scala.Tuple2[K, V]]], r: Rep[scala.Function1[scala.Tuple2[V, V], V]]): Rep[Coll[scala.Tuple2[K, V]]] = delayInvoke;
      @NeverInline override def unionSet(that: Rep[Coll[A]]): Rep[Coll[A]] = delayInvoke
    };
    abstract class CollOverArrayBuilder extends CollBuilder {
      override def Monoids: Rep[MonoidBuilder] = RMonoidBuilderInst();
      def pairColl[A, B](as: Rep[Coll[A]], bs: Rep[Coll[B]]): Rep[PairColl[A, B]] = RPairOfCols(as, bs);
      @NeverInline @Reified(value = "T") def fromItems[T](items: Rep[T]*)(implicit cT: Elem[T]): Rep[Coll[T]] = delayInvoke;
      @NeverInline def fromArray[T](arr: Rep[WArray[T]]): Rep[Coll[T]] = delayInvoke;
      @NeverInline def replicate[T](n: Rep[Int], v: Rep[T]): Rep[Coll[T]] = delayInvoke;
      @NeverInline def unzip[A, B](xs: Rep[Coll[scala.Tuple2[A, B]]]): Rep[scala.Tuple2[Coll[A], Coll[B]]] = delayInvoke;
      @NeverInline def xor(left: Rep[Coll[Byte]], right: Rep[Coll[Byte]]): Rep[Coll[Byte]] = delayInvoke;
      @NeverInline override def emptyColl[T](implicit cT: Elem[T]): Rep[Coll[T]] = delayInvoke;
      @NeverInline override def outerJoin[K, L, R, O](left: Rep[Coll[scala.Tuple2[K, L]]], right: Rep[Coll[scala.Tuple2[K, R]]])(l: Rep[scala.Function1[scala.Tuple2[K, L], O]], r: Rep[scala.Function1[scala.Tuple2[K, R], O]], inner: Rep[scala.Function1[scala.Tuple2[K, scala.Tuple2[L, R]], O]]): Rep[Coll[scala.Tuple2[K, O]]] = delayInvoke;
      @NeverInline override def flattenColl[A](coll: Rep[Coll[Coll[A]]]): Rep[Coll[A]] = delayInvoke
    };
    abstract class PairOfCols[L, R](val ls: Rep[Coll[L]], val rs: Rep[Coll[R]]) extends PairColl[L, R] {
      override def builder: Rep[CollBuilder] = RCollOverArrayBuilder();
      override def toArray: Rep[WArray[scala.Tuple2[L, R]]] = PairOfCols.this.ls.toArray.zip(PairOfCols.this.rs.toArray);
      override def length: Rep[Int] = PairOfCols.this.ls.length;
      override def apply(i: Rep[Int]): Rep[scala.Tuple2[L, R]] = Pair(PairOfCols.this.ls.apply(i), PairOfCols.this.rs.apply(i));
      @NeverInline override def isEmpty: Rep[Boolean] = delayInvoke;
      @NeverInline override def nonEmpty: Rep[Boolean] = delayInvoke;
      @NeverInline override def isDefinedAt(idx: Rep[Int]): Rep[Boolean] = delayInvoke;
      @NeverInline override def getOrElse(i: Rep[Int], default: Rep[scala.Tuple2[L, R]]): Rep[scala.Tuple2[L, R]] = delayInvoke;
      @NeverInline override def map[V](f: Rep[scala.Function1[scala.Tuple2[L, R], V]]): Rep[Coll[V]] = delayInvoke;
      @NeverInline override def exists(p: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]]): Rep[Boolean] = delayInvoke;
      @NeverInline override def forall(p: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]]): Rep[Boolean] = delayInvoke;
      @NeverInline override def filter(p: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]]): Rep[Coll[scala.Tuple2[L, R]]] = delayInvoke;
      @NeverInline override def foldLeft[B](zero: Rep[B], op: Rep[scala.Function1[scala.Tuple2[B, scala.Tuple2[L, R]], B]]): Rep[B] = delayInvoke;
      override def slice(from: Rep[Int], until: Rep[Int]): Rep[PairColl[L, R]] = PairOfCols.this.builder.pairColl[L, R](PairOfCols.this.ls.slice(from, until), PairOfCols.this.rs.slice(from, until));
      def append(other: Rep[Coll[scala.Tuple2[L, R]]]): Rep[Coll[scala.Tuple2[L, R]]] = {
        val arrs: Rep[scala.Tuple2[Coll[L], Coll[R]]] = PairOfCols.this.builder.unzip[L, R](other);
        PairOfCols.this.builder.pairColl[L, R](PairOfCols.this.ls.append(arrs._1), PairOfCols.this.rs.append(arrs._2))
      };
      override def reverse: Rep[Coll[scala.Tuple2[L, R]]] = PairOfCols.this.builder.pairColl[L, R](PairOfCols.this.ls.reverse, PairOfCols.this.rs.reverse);
      @NeverInline override def sum(m: Rep[Monoid[scala.Tuple2[L, R]]]): Rep[scala.Tuple2[L, R]] = delayInvoke;
      def zip[B](ys: Rep[Coll[B]]): Rep[PairColl[scala.Tuple2[L, R], B]] = PairOfCols.this.builder.pairColl[scala.Tuple2[L, R], B](this, ys);
      override def indices: Rep[Coll[Int]] = PairOfCols.this.ls.indices;
      @NeverInline override def flatMap[B](f: Rep[scala.Function1[scala.Tuple2[L, R], Coll[B]]]): Rep[Coll[B]] = delayInvoke;
      @NeverInline override def segmentLength(p: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]], from: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def indexWhere(p: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]], from: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def lastIndexWhere(p: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]], end: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def take(n: Rep[Int]): Rep[Coll[scala.Tuple2[L, R]]] = delayInvoke;
      @NeverInline override def partition(pred: Rep[scala.Function1[scala.Tuple2[L, R], Boolean]]): Rep[scala.Tuple2[Coll[scala.Tuple2[L, R]], Coll[scala.Tuple2[L, R]]]] = delayInvoke;
      @NeverInline override def patch(from: Rep[Int], patch: Rep[Coll[scala.Tuple2[L, R]]], replaced: Rep[Int]): Rep[Coll[scala.Tuple2[L, R]]] = delayInvoke;
      @NeverInline override def updated(index: Rep[Int], elem: Rep[scala.Tuple2[L, R]]): Rep[Coll[scala.Tuple2[L, R]]] = delayInvoke;
      @NeverInline override def updateMany(indexes: Rep[Coll[Int]], values: Rep[Coll[scala.Tuple2[L, R]]]): Rep[Coll[scala.Tuple2[L, R]]] = delayInvoke;
      @NeverInline override def mapReduce[K, V](m: Rep[scala.Function1[scala.Tuple2[L, R], scala.Tuple2[K, V]]], r: Rep[scala.Function1[scala.Tuple2[V, V], V]]): Rep[Coll[scala.Tuple2[K, V]]] = delayInvoke;
      @NeverInline override def unionSet(that: Rep[Coll[scala.Tuple2[L, R]]]): Rep[Coll[scala.Tuple2[L, R]]] = delayInvoke;
      @NeverInline override def mapFirst[T1](f: Rep[scala.Function1[L, T1]]): Rep[Coll[scala.Tuple2[T1, R]]] = delayInvoke;
      @NeverInline override def mapSecond[T1](f: Rep[scala.Function1[R, T1]]): Rep[Coll[scala.Tuple2[L, T1]]] = delayInvoke
    };
    abstract class CReplColl[A](val value: Rep[A], val length: Rep[Int]) extends ReplColl[A] {
      def builder: Rep[CollBuilder] = RCollOverArrayBuilder();
      @NeverInline def toArray: Rep[WArray[A]] = delayInvoke;
      @NeverInline def apply(i: Rep[Int]): Rep[A] = delayInvoke;
      @NeverInline override def isEmpty: Rep[Boolean] = delayInvoke;
      @NeverInline override def nonEmpty: Rep[Boolean] = delayInvoke;
      @NeverInline override def isDefinedAt(idx: Rep[Int]): Rep[Boolean] = delayInvoke;
      @NeverInline def getOrElse(i: Rep[Int], default: Rep[A]): Rep[A] = delayInvoke;
      def map[B](f: Rep[scala.Function1[A, B]]): Rep[Coll[B]] = RCReplColl(f.apply(CReplColl.this.value), CReplColl.this.length);
      @NeverInline def foreach(f: Rep[scala.Function1[A, Unit]]): Rep[Unit] = delayInvoke;
      @NeverInline def exists(p: Rep[scala.Function1[A, Boolean]]): Rep[Boolean] = delayInvoke;
      @NeverInline def forall(p: Rep[scala.Function1[A, Boolean]]): Rep[Boolean] = delayInvoke;
      @NeverInline def filter(p: Rep[scala.Function1[A, Boolean]]): Rep[Coll[A]] = delayInvoke;
      @NeverInline def foldLeft[B](zero: Rep[B], op: Rep[scala.Function1[scala.Tuple2[B, A], B]]): Rep[B] = delayInvoke;
      def zip[B](ys: Rep[Coll[B]]): Rep[PairColl[A, B]] = CReplColl.this.builder.pairColl[A, B](this, ys);
      @NeverInline def slice(from: Rep[Int], until: Rep[Int]): Rep[Coll[A]] = delayInvoke;
      @NeverInline def append(other: Rep[Coll[A]]): Rep[Coll[A]] = delayInvoke;
      override def reverse: Rep[Coll[A]] = this;
      def sum(m: Rep[Monoid[A]]): Rep[A] = m.power(CReplColl.this.value, CReplColl.this.length);
      @NeverInline override def indices: Rep[Coll[Int]] = delayInvoke;
      @NeverInline override def flatMap[B](f: Rep[scala.Function1[A, Coll[B]]]): Rep[Coll[B]] = delayInvoke;
      @NeverInline override def segmentLength(p: Rep[scala.Function1[A, Boolean]], from: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def indexWhere(p: Rep[scala.Function1[A, Boolean]], from: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def lastIndexWhere(p: Rep[scala.Function1[A, Boolean]], end: Rep[Int]): Rep[Int] = delayInvoke;
      @NeverInline override def take(n: Rep[Int]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def partition(pred: Rep[scala.Function1[A, Boolean]]): Rep[scala.Tuple2[Coll[A], Coll[A]]] = delayInvoke;
      @NeverInline override def patch(from: Rep[Int], patch: Rep[Coll[A]], replaced: Rep[Int]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def updated(index: Rep[Int], elem: Rep[A]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def updateMany(indexes: Rep[Coll[Int]], values: Rep[Coll[A]]): Rep[Coll[A]] = delayInvoke;
      @NeverInline override def mapReduce[K, V](m: Rep[scala.Function1[A, scala.Tuple2[K, V]]], r: Rep[scala.Function1[scala.Tuple2[V, V], V]]): Rep[Coll[scala.Tuple2[K, V]]] = delayInvoke;
      @NeverInline override def unionSet(that: Rep[Coll[A]]): Rep[Coll[A]] = delayInvoke
    };
    trait CollOverArrayCompanion;
    trait CollOverArrayBuilderCompanion;
    trait PairOfColsCompanion;
    trait CReplCollCompanion
  }
}
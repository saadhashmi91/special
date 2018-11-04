package special.collection {
  import scalan._

  trait Cols extends Base { self: Library =>
    import Col._;
    import ColBuilder._;
    import PairCol._;
    import WArray._;
    @ContainerType @FunctorType @Liftable trait Col[A] extends Def[Col[A]] {
      implicit def eA: Elem[A];
      def builder: Rep[ColBuilder];
      def arr: Rep[WArray[A]];
      def length: Rep[Int];
      def apply(i: Rep[Int]): Rep[A];
      def getOrElse(i: Rep[Int], default: Rep[A]): Rep[A];
      def map[B](f: Rep[scala.Function1[A, B]]): Rep[Col[B]];
      def zip[B](ys: Rep[Col[B]]): Rep[PairCol[A, B]];
      def foreach(f: Rep[scala.Function1[A, Unit]]): Rep[Unit];
      def exists(p: Rep[scala.Function1[A, Boolean]]): Rep[Boolean];
      def forall(p: Rep[scala.Function1[A, Boolean]]): Rep[Boolean];
      def filter(p: Rep[scala.Function1[A, Boolean]]): Rep[Col[A]];
      def where(p: Rep[scala.Function1[A, Boolean]]): Rep[Col[A]] = this.filter(p);
      def fold[B](zero: Rep[B], op: Rep[scala.Function1[scala.Tuple2[B, A], B]]): Rep[B];
      def sum(m: Rep[Monoid[A]]): Rep[A];
      def slice(from: Rep[Int], until: Rep[Int]): Rep[Col[A]];
      def append(other: Rep[Col[A]]): Rep[Col[A]]
    };
    trait PairCol[L, R] extends Col[scala.Tuple2[L, R]] {
      implicit def eL: Elem[L];
      implicit def eR: Elem[R];
      def ls: Rep[Col[L]];
      def rs: Rep[Col[R]]
    };
    trait ReplCol[A] extends Col[A] {
      implicit def eA: Elem[A];
      def value: Rep[A];
      def length: Rep[Int]
    };
    @Liftable trait ColBuilder extends Def[ColBuilder] {
      def pairCol[A, B](as: Rep[Col[A]], bs: Rep[Col[B]]): Rep[PairCol[A, B]];
      @Reified(value = "T") def fromItems[T](items: Rep[T]*)(implicit cT: Elem[T]): Rep[Col[T]];
      @NeverInline def unzip[A, B](xs: Rep[Col[scala.Tuple2[A, B]]]): Rep[scala.Tuple2[Col[A], Col[B]]] = delayInvoke;
      def xor(left: Rep[Col[Byte]], right: Rep[Col[Byte]]): Rep[Col[Byte]];
      def fromArray[T](arr: Rep[WArray[T]]): Rep[Col[T]];
      def replicate[T](n: Rep[Int], v: Rep[T]): Rep[Col[T]]
    };
    trait ColCompanion;
    trait PairColCompanion;
    trait ReplColCompanion;
    trait ColBuilderCompanion
  }
}
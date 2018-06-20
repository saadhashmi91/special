package scalan.collection {
  import scalan._

  trait ConcreteCosts extends Base { self: Library =>
    trait Closure[Env, Arg, Res] extends Def[Closure[Env, Arg, Res]] {
      implicit def eEnv: Elem[Env];
      implicit def eArg: Elem[Arg];
      implicit def eRes: Elem[Res];
      def env: Rep[Env];
      @OverloadId(value = "apply_with_env") def apply(e: Rep[Env], a: Rep[Arg]): Rep[Res];
      @OverloadId(value = "apply") def apply(a: Rep[Arg]): Rep[Res] = this.apply(Closure.this.env, a)
    };
    abstract class CostedPrim[Val](val value: Rep[Val], val cost: Rep[Long]) extends Costed[Val] {
      def builder: Rep[ConcreteCostedBuilder] = ConcreteCostedBuilder()
    };
    abstract class CostedPair[L, R](val l: Rep[Costed[L]], val r: Rep[Costed[R]]) extends Costed[scala.Tuple2[L, R]] {
      def builder: Rep[ConcreteCostedBuilder] = ConcreteCostedBuilder();
      def value: Rep[scala.Tuple2[L, R]] = Pair(CostedPair.this.l.value, CostedPair.this.r.value);
      def cost: Rep[Long] = CostedPair.this.l.cost.+(CostedPair.this.r.cost).+(toRep(1.asInstanceOf[Int]))
    };
    abstract class ClosureBase[Env, Arg, Res](val env: Rep[Env], val func: Rep[scala.Function1[scala.Tuple2[Env, Arg], Res]]) extends Closure[Env, Arg, Res] {
      def apply(e: Rep[Env], a: Rep[Arg]): Rep[Res] = ClosureBase.this.func.apply(Pair(e, a))
    };
    abstract class CostedFunc[Env, Arg, Res](val envCost: Rep[Costed[Env]], val func: Rep[Closure[Env, Arg, Res]], val costFunc: Rep[Closure[Env, Arg, Long]]) extends Costed[scala.Function1[Arg, Res]] {
      implicit def eArg: Elem[Arg]
      def builder: Rep[ConcreteCostedBuilder] = ConcreteCostedBuilder();
      def value: Rep[scala.Function1[Arg, Res]] = fun(((a: Rep[Arg]) => CostedFunc.this.func.apply(a)));
      def cost: Rep[Long] = CostedFunc.this.envCost.cost.+(toRep(1L.asInstanceOf[Long]))
    };
    abstract class CostedArray[Item](val values: Rep[Col[Item]], val costs: Rep[Col[Long]]) extends Costed[WArray[Item]] {
      def builder: Rep[ConcreteCostedBuilder] = ConcreteCostedBuilder();
      def value: Rep[WArray[Item]] = CostedArray.this.values.arr;
      def cost: Rep[Long] = CostedArray.this.costs.sum(CostedArray.this.builder.monoidBuilder.longPlusMonoid)
    };
    abstract class CostedPairArray[L, R](val ls: Rep[Costed[WArray[L]]], val rs: Rep[Costed[WArray[R]]]) extends Costed[WArray[scala.Tuple2[L, R]]] {
      def builder: Rep[ConcreteCostedBuilder] = ConcreteCostedBuilder();
      def value: Rep[WArray[scala.Tuple2[L, R]]] = CostedPairArray.this.ls.value.zip(CostedPairArray.this.rs.value);
      def cost: Rep[Long] = CostedPairArray.this.ls.cost.+(CostedPairArray.this.rs.cost).+(CostedPairArray.this.ls.value.length.toLong)
    };
    abstract class CostedNestedArray[Item](val rows: Rep[Col[Costed[WArray[Item]]]]) extends Costed[WArray[WArray[Item]]] {
      implicit def eItem: Elem[Item]
      def builder: Rep[ConcreteCostedBuilder] = ConcreteCostedBuilder();
      def value: Rep[WArray[WArray[Item]]] = CostedNestedArray.this.rows.map[WArray[Item]](fun(((r: Rep[Costed[WArray[Item]]]) => r.value))).arr;
      def cost: Rep[Long] = CostedNestedArray.this.rows.map[Long](fun(((r: Rep[Costed[WArray[Item]]]) => r.cost))).sum(CostedNestedArray.this.builder.monoidBuilder.longPlusMonoid)
    };
    abstract class ConcreteCostedBuilder extends CostedBuilder {
      def monoidBuilder: Rep[MonoidBuilderInst] = MonoidBuilderInst()
    };
    trait ClosureCompanion;
    trait CostedPrimCompanion;
    trait CostedPairCompanion;
    trait ClosureBaseCompanion;
    trait CostedFuncCompanion;
    trait CostedArrayCompanion;
    trait CostedPairArrayCompanion;
    trait CostedNestedArrayCompanion;
    trait ConcreteCostedBuilderCompanion
  }
}
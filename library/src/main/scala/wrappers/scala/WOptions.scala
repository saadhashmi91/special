package wrappers.scala {
  import scalan._

  import impl._

  import special.wrappers.WrappersModule

  import special.wrappers.OptionWrapSpec

  import scala.collection.mutable.WrappedArray

  trait WOptions extends Base { self: WrappersModule =>
    import WOption._;
    @External("Option") @ContainerType @FunctorType @Liftable @WithMethodCallRecognizers trait WOption[A] extends Def[WOption[A]] {
      implicit def eA: Elem[A];
      @External def fold[B](ifEmpty: Rep[Thunk[B]], f: Rep[scala.Function1[A, B]]): Rep[B];
      @External def isEmpty: Rep[Boolean];
      @External def isDefined: Rep[Boolean];
      @External def filter(p: Rep[scala.Function1[A, Boolean]]): Rep[WOption[A]];
      @External def flatMap[B](f: Rep[scala.Function1[A, WOption[B]]]): Rep[WOption[B]];
      @External def map[B](f: Rep[scala.Function1[A, B]]): Rep[WOption[B]];
      @External def getOrElse[B](default: Rep[Thunk[B]]): Rep[B];
      @External def get: Rep[A]
    };
    trait WOptionCompanion
  }
}
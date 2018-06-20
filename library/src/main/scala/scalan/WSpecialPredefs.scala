package scalan {
  import scalan._

  import impl._

  import scala.wrappers.WrappersModule

  trait WSpecialPredefs extends Base { self: WrappersModule =>
    @External("SpecialPredef") trait WSpecialPredef extends Def[WSpecialPredef];
    trait WSpecialPredefCompanion {
      @External def loopUntil[A](s1: Rep[A], isMatch: Rep[scala.Function1[A, Boolean]], step: Rep[scala.Function1[A, A]]): Rep[A]
    }
  }
}
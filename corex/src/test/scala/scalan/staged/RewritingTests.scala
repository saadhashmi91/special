package scalan
package staged

import scala.language.reflectiveCalls
import scalan.compilation.{DummyCompiler, Passes}

class RewritingTests extends BaseCtxTests {

  trait TestTransform extends Passes {
    import scalan._

    val passName = "testTransformPass"
    lazy val builder = constantPass[GraphPass](passName, b => new GraphTransformPass(b, passName, DefaultMirror, NoRewriting))

    def doTransform[A](e: Rep[A]): Sym = {
      val g0 = new PGraph(e)
      val pass = builder(g0)
      val g = pass.apply(g0)
      g.roots.head
    }
  }

  trait Prog0 extends Scalan {

    lazy val mkRightFun = fun { x: Rep[Int] => x.asRight[Int] }

    override def rewriteDef[T](d: Def[T]): Sym = d match {
      // rewrite fun(x => Right(_)) to fun(x => Left(x))
      case lam: Lambda[a, |[_, b]] @unchecked =>
        lam.y match {
          case Def(r @ SRight(_,_)) =>
            implicit val eA = r.eLeft.asInstanceOf[Elem[b]]
            fun { x: Rep[b] => x.asLeft[b] }
          case _ => super.rewriteDef(d)
        }
      case _ =>
        super.rewriteDef(d)
    }
  }

  val p0 = new TestContextEx("RewritingRootLambda") with Prog0 { p0 =>
    val passes = new TestTransform {
      val scalan: p0.type = p0
    }

    def testMkRight(): Unit = {
      emit("mkRightFun", mkRightFun)
      val newLambda = passes.doTransform(mkRightFun)
      emit("mkRightFun'", newLambda)

      inside(newLambda) { case Def(Lambda(_, _, x, Def(SLeft(l,_)))) =>
        assert(x == l)
      }
    }
  }

  trait Prog extends Scalan {
    lazy val ifFold = fun { pp: Rep[Boolean] =>
      val e1 = toRep(1.0).asLeft[Int]
      val e2 = toRep(2).asRight[Double]
      val iff = ifThenElseLazy(pp, e1, e2)
      iff.foldBy(constFun(10), constFun(100))
    }

    lazy val ifIfFold = fun2 { (p1: Rep[Boolean], p2: Rep[Boolean]) =>
      val e1 = toRep(1.0).asLeft[Int]
      val e2 = toRep(2).asRight[Double]
      val e3 = toRep(3).asRight[Double]
      val iff = ifThenElseLazy(
        p1,
        ifThenElseLazy(p2, e1, e2),
        e3)
      iff.foldBy(constFun(10), constFun(100))
    }
  }

  val p = new TestCompilerContext("RewritingRules") {
    val compiler = new DummyCompiler(new ScalanEx with Prog) with TestTransform
    import compiler.scalan._

    def testIfFold(): Unit = {
      emit("ifFold", ifFold)
      ifFold.getLambda.y should matchPattern { case Def(IfThenElseLazy(_, Def(ThunkDef(_, Def(Const(10)))), Def(ThunkDef(_, Def(Const(100)))))) => }
    }

    def testIfIfFold(): Unit = {
      emit("ifIfFold", ifIfFold)
      ifIfFold.getLambda.y should matchPattern {
        case Def(IfThenElseLazy(c1,
           Def(ThunkDef(_, Def(IfThenElseLazy(c2,
             Def(ThunkDef(_, Def(Const(10)))),
             Def(ThunkDef(_, Def(Const(100))))
           )))),
           Def(ThunkDef(_, Def(Const(100))))
           )) =>
      }
    }
  }

  ignore("root Lambda")(p0.testMkRight)

  //TODO implement for Lazy (the following tests were ignored when strict IfThenElse removed)
  ignore("SumFold(IfThenElse(p, t, e)) -> IfThenElse(p, SumFold, SumFold)")(p.testIfFold)
  ignore("SumFold(IfThenElse(p, IfThenElse, e) -> IfThenElse(p, IfThenElse(p, SumFold, SumFold), SumFold)")(p.testIfIfFold)
}

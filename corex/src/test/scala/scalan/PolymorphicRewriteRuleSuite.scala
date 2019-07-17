package scalan

import _root_.scalan.compilation.GraphVizExport

class PolymorphicRewriteRuleSuite extends BaseShouldTests {

  class Ctx extends Scalan with GraphVizExport {
    //    lazy val testLemma = postulate[Int, Int, Int, Int]((x, y, z) => x * y + x * z <=> x * (y + z))
    //    lazy val rule = patternRewriteRule(testLemma)
    //    lazy val patGraph = rule.patternGraph
    //
    //    lazy val test = {(x: IntRep) => x * 10 + x * 20}
    //    lazy val testFunc = fun(test)
    case class Id[T](x: Rep[T])(implicit selfType: Elem[T]) extends BaseDef[T] {
      override def transform(t: Transformer) = Id(t(x))
    }
    // We do _not_ want to use rewrite
    //    override def rewriteDef[T](d: Def[T]) = d match {
    //      case Id(x) => x
    //      case _ => super.rewriteDef(d)
    //    }
  }

  def getCtx = new Ctx

  "Scalan" should "rewrite id" in {
    pending
    val ctx = getCtx
    import ctx._
    val c0: Rep[Int] = Const(0)
    val ic0: Rep[Int] = Id(Const(0))
    ic0 should equal(c0)
  }
}
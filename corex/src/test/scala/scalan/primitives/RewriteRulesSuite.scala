package scalan.primitives

import java.lang.reflect.Method

import scalan.compilation.{GraphVizConfig, GraphVizExport}
import scalan.{BaseShouldTests, ScalanEx, Nullable}
import scalan.util.CollectionUtil._

class RewriteRulesSuite extends BaseShouldTests {

  class Ctx extends ScalanEx with GraphVizExport {
    override def isInvokeEnabled(d: Def[_], m: Method) = true
    lazy val testLemma = postulate[Int, Int, Int, Int]((x, y, z) => x * y + x * z <=> x * (y + z))
    lazy val rule = patternRewriteRule(testLemma)

    lazy val test = {(x: Ref[Int]) => x * 10 + x * 20}
    lazy val testFunc = fun(test)
  }

  def getCtx = new Ctx

  "ScalanStaged" should "created Lemma" in {
    val ctx = getCtx
    ctx.emitDepGraph(ctx.testLemma, prefix, "testLemma")(GraphVizConfig.default)
  }

  it should "create LemmaRule" in {
    val ctx = getCtx
    import ctx._
    ctx.emitDepGraph(Seq[Sym](testLemma, rule.lhs, rule.rhs), prefix, "testRule")(GraphVizConfig.default)
  }

  it should "recognize pattern" in {
    val ctx = getCtx
    import ctx._
    val lam = testFunc.getLambda
    ctx.emitDepGraph(List(rule.lhs, testFunc), prefix, "LemmaRule/patternAndTestFunc")(GraphVizConfig.default)
    patternMatch(rule.lhs, lam.y) match {
      case Nullable(subst) =>
        subst.toImmutableMap should not be(Map.empty)
      case _ => 
        fail("should recognize pattern")
    }
    
  }

  it should "apply pattern" in {
    val ctx = getCtx
    import ctx._
    val lam = testFunc.getLambda
    val rewritten = rule(lam.y)
    rewritten match {
      case res if res != null =>
        ctx.emitDepGraph(List(Pair(lam.y, res)), prefix, "LemmaRule/originalAndRewritten")(GraphVizConfig.default)
      case _ => 
        fail("should apply pattern")
    }
  }

  it should "rewrite when registered" in {
    val ctx = getCtx
    import ctx._
    val withoutRule = testFunc
    addRewriteRules(rule)
    val withRule = fun(test)
    removeRewriteRules(rule)
    ctx.emitDepGraph(List(withoutRule, withRule), prefix, "LemmaRule/ruleRewriting")(GraphVizConfig.default)
    
    val expected = fun[Int,Int] {x => x * 30}
    alphaEqual(withRule, expected) should be(true)
    alphaEqual(withoutRule, expected) should be(false)

    val afterRemoval = fun(test)
    ctx.emitDepGraph(List(withoutRule, withRule, afterRemoval), prefix, "LemmaRule/ruleRewriting")(GraphVizConfig.default)
    alphaEqual(afterRemoval, withoutRule) should be(true)
  }
}

package scalan.primitives

import scala.language.reflectiveCalls
import scalan.common.{SegmentsModule}
import scalan.BaseCtxTests

class FunctionTests extends BaseCtxTests {

  test("identity functions equality works") {
    val ctx = new TestContext("identityFuns") with SegmentsModule {
      lazy val t1 = identityFun[Int]
      lazy val t2 = identityFun[Int]
      lazy val t3 = identityFun[Double]
    }
    import ctx._
    t1 shouldEqual t2
    t1 shouldNot equal(t3)
  }

  test("IdentityLambda matcher works") {
    val ctx = new TestContext("identityFuns2") {
      lazy val t1 = constFun[Int, Int](1)
      lazy val t2 = fun { x: Ref[Int] => fun { y: Ref[Int] => x } }
    }
    import ctx._

    t1 shouldNot matchPattern { case Def(IdentityLambda()) => }
    t2.getLambda.y shouldNot matchPattern { case Def(IdentityLambda()) => }

    identityFun[Int] should matchPattern { case Def(IdentityLambda()) => }
    fun[Int, Int] { x => x } should matchPattern { case Def(IdentityLambda()) => }
    fun[Int, Int] { x => (x + 1) * 2 } shouldNot matchPattern { case Def(IdentityLambda()) => }
  }

  test("const functions equality works") {
    val ctx = new TestContext("constFuns1") {
      lazy val t1 = constFun[Int, Int](1)
      lazy val t2 = constFun[Int, Int](1)
      lazy val t3 = constFun[Double, Int](1)
    }
    import ctx._

    t1 shouldEqual t2
    t1 shouldNot equal(t3)
  }

  test("ConstantLambda matcher works") {
    val ctx = new TestContext("constFuns2") {
      lazy val t1 = constFun[Int, Int](1)
      lazy val t2 = fun { x: Ref[Int] => fun { y: Ref[Int] => x } }
    }
    import ctx._

    t1 should matchPattern { case Def(ConstantLambda(_)) => }
    t1 should matchPattern { case Def(VeryConstantLambda(_)) => }

    t2.getLambda.y should matchPattern { case Def(ConstantLambda(_)) => }
    t2.getLambda.y shouldNot matchPattern { case Def(VeryConstantLambda(_)) => }

    identityFun[Int] shouldNot matchPattern { case Def(ConstantLambda(_)) => }
    fun[Int, Int] { x => x + 1 } shouldNot matchPattern { case Def(ConstantLambda(_)) => }
    fun[Int, Int] { x => (x + 1) * 2 } shouldNot matchPattern { case Def(ConstantLambda(_)) => }
  }

  test("Alpha-equivalence works") {
    val ctx = new TestContext("alphaEquivalence") {
      lazy val idInt1 = fun[Int, Int] { x => x }
      lazy val idInt2 = fun[Int, Int] { y => y }
      lazy val idDouble = fun[Double, Double] { x => x }
      lazy val f1_1 = fun { x: Ref[Int] => fun { y: Ref[Int] => x } }
      lazy val f1_2 = fun { y: Ref[Int] => fun { x: Ref[Int] => y } }
      lazy val f2_1 = fun { y: Ref[Int] => fun { x: Ref[Int] => x } }
      lazy val f2_2 = fun { x: Ref[Int] => fun { y: Ref[Int] => y } }
      lazy val f3_1 = fun { x: Ref[Int] => x + 1 }
      lazy val f3_2 = fun { y: Ref[Int] => y + 1 }
      lazy val f4 = fun { x: Ref[Int] => x + 2 }
    }
    import ctx._

    alphaEqual(idInt1, idInt2) should be(true)
    alphaEqual(f1_1, f1_2) should be(true)
    alphaEqual(f2_1, f2_2) should be(true)
    alphaEqual(f3_1, f3_2) should be(true)
    alphaEqual(idInt1, f3_1) should be(false)
    alphaEqual(f1_1, f2_1) should be(false)
    alphaEqual(f3_1, f4) should be(false)

    // alpha-equivalence implies equality for lambdas
    idInt1 shouldEqual idInt2
    f1_1 shouldEqual f1_2
    f2_1 shouldEqual f2_2
    f3_1 shouldEqual f3_2
  }

  test("Alpha-equivalence can be turned off") {
    class Ctx(alphaEquality: Boolean) extends TestContext(s"alphaEquivalence=$alphaEquality") {
      this.useAlphaEquality = alphaEquality
      lazy val idInt1 = fun[Int, Int] { x => x }
      lazy val idInt2 = fun[Int, Int] { y => y }
      lazy val f3_1 = fun { x: Ref[Int] => x + 1 }
      lazy val f3_2 = fun { y: Ref[Int] => y + 1 }
    }

    val ctx = new Ctx(alphaEquality = false)
    import ctx._
    idInt1 shouldBe idInt1
    (idInt1 == idInt2) shouldBe false
    (f3_1 == f3_2) shouldBe false
  }
}

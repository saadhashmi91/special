package scalan.common

import scala.language.reflectiveCalls
import scalan._

class ConverterTests extends BaseCtxTests {

  trait ConvProg extends CommonExamples {
  //TODO uncomment after convertTo works not only for Def[_]
//    lazy val t20 = fun { in: Rep[Array[Interval]] => in.convertTo[Array[Slice]] }
  }

  class ConvProgStaged extends TestContext with ConvProg with SegmentsModule {
  }
//  class ConvProgStd extends ScalanDslStd with ConvProg with SegmentsDslStd {
//  }

  ignore("simple converter tests") {
    val ctx = new ConvProgStaged
    ctx.emit("t1", ctx.t1)
    ctx.emit("t2", ctx.t2)
    ctx.emit("t3", ctx.t3)
    ctx.emit("t4", ctx.t4)
    ctx.emit("t5", ctx.t5)
  }

  // TODO can't convert from Segment to Centered. Should we be able to?
  ignore("convertToCentered") {
    val ctx = new ConvProgStaged
    ctx.emit("t6", ctx.t6)
  }

  ignore("converIfThenElse") {
    val ctx = new ConvProgStaged
    ctx.emit("t7", ctx.t7)
    ctx.emit("t9", ctx.t9)
  }

  ignore("converIfThenElseWithPair") {
    val ctx = new ConvProgStaged
    ctx.emit("t8", ctx.t8)
  }

  ignore("convertIfThenElseWithOption") {
    val ctx = new ConvProgStaged
    ctx.emit("t10", ctx.t10)
    ctx.emit("t10_1", ctx.t10_1)
    ctx.emit("t10_2", ctx.t10_2)
    ctx.emit("t10_3", ctx.t10_3)
    ctx.emit("t10_4", ctx.t10_4)
    ctx.emit("t10_5", ctx.t10_5)
  }

  ignore("convertIfThenElseWithSum") {
    val ctx = new ConvProgStaged
    ctx.emit("t11", ctx.t11)
    ctx.emit("t12", ctx.t12)
  }

  test("convertSumFold") {
    val ctx = new ConvProgStaged
//    ctx.emit("t13", ctx.t13)
  }

  def testConverter[A,B](ctx: ConvProgStaged, name: String, shouldConvert: Boolean = true)(implicit eA: ctx.Elem[A], eB: ctx.Elem[B]) = {
    import ctx._
    val conv = getConverter(eA,eB)
    if (shouldConvert) {
      assert(conv.isDefined, s"no converter $eA --> $eB")
      ctx.emit(name, conv.get)
    } else {
      val fileName = "unexpected_" + name
      if (conv.isDefined)
        ctx.emit(fileName, conv.get)
      assert(!conv.isDefined, s"unexpected converter $eA --> $eB, see $fileName")
    }
  }

  test("getConverter") {
    val ctx = new ConvProgStaged
    import ctx._
    import Segment._
    import Slice._
    import Interval._
    import Centered._
    testConverter[Int, Int](ctx, "convInt")
    testConverter[Int, Double](ctx, "convIntToDouble",false)
    testConverter[(Int,Int), (Int,Int)](ctx, "convPairOfInt")
    testConverter[Interval, Interval](ctx, "convEntityItself")
    testConverter[Interval, Slice](ctx, "convIsoEntities")
    testConverter[Interval, Segment](ctx, "convToSuperType")
    testConverter[Segment, Interval](ctx, "convFromSuperType")
    testConverter[(Interval,Slice), (Slice,Interval)](ctx, "convPairOfIsoEntities")
//    testConverter[Array[Interval], Array[Slice]](ctx, "convArray")
//    testConverter[(Array[Interval],Array[Slice]), (Array[Slice],Array[Interval])](ctx, "convPairOfArrays")
//    testConverter[Array[Array[Interval]], Array[Array[Slice]]](ctx, "convNArray")
//    testConverter[Array[Array[Interval]], Array[Slice]](ctx, "convNArrayToArray", false)
  }

  test("identityConv") {
    val ctx = new ConvProgStaged
    import ctx._; import Converter._
    
    val idInt = identityConv[Int]
    val idDouble = identityConv[Double]
    assert(idInt.isIdentity && idDouble.isIdentity)
    val pair = pairConv(idInt, idDouble)
    emit("pair", pair)
    assert(pair.isIdentity)
    val comp = composeConv(pair, pair)
    emit("comp", comp)
    assert(comp.isIdentity)

    val baseInt = baseConv(fun { x: Rep[Int] => x + 1 })
    emit("baseInt", baseInt)
    assert(!baseInt.isIdentity)
    val pairNon = pairConv(baseInt, idDouble)
    emit("pairNon", pairNon)
    assert(!pairNon.isIdentity)
    val compNon = composeConv(pairNon, pair)
    emit("compNon", compNon)
    assert(!compNon.isIdentity)
    val compNon2 = composeConv(pair, pairNon)
    emit("compNon2", compNon2)
    assert(!compNon2.isIdentity)
  }

}

package scalan.common

import scala.language.reflectiveCalls
import scalan._

trait SegmentMethodWrappers extends Scalan with SegmentsModule {
  import Segment._
  import Slice._
  import Interval._
  import Centered._
  lazy val Interval_start  = fun { (in: Rep[IntervalData]) => RInterval(in).start }
  lazy val Slice_start     = fun { (in: Rep[SliceData]) => RSlice(in).end }
  lazy val Interval_length = fun { (in: Rep[IntervalData]) => RInterval(in).length }
  lazy val Slice_length    = fun { (in: Rep[SliceData]) => RSlice(in).length }
  lazy val Interval_end    = fun { (in: Rep[IntervalData]) => RInterval(in).end }
  lazy val Slice_end       = fun { (in: Rep[SliceData]) => RSlice(in).end }
  lazy val Interval_shift  = fun { (in: Rep[(IntervalData, Int)]) => val Pair(i, o) = in; RInterval(i).shift(o) }
  lazy val Slice_shift     = fun { (in: Rep[(SliceData, Int)]) => val Pair(i, o) = in; RSlice(i).shift(o) }
}

class SegmentMethodWrappersTests extends BaseNestedCtxTests {

  class SegmentMethodWrappersStaged(testName: String)
    extends TestContext(testName) with SegmentMethodWrappers with SegmentsModule {
  }

  val ctx = new SegmentMethodWrappersStaged("start")
  describe("Start") {
    it("interval") {
      ctx.emit("Interval_start", ctx.Interval_start)
    }
    it("slice") {
      ctx.emit("Slice_start", ctx.Slice_start)
    }
  }
  describe("Length") {
    it("interval") {
      ctx.emit("Interval_length", ctx.Interval_length)
    }
    it("slice") {
      ctx.emit("Slice_length", ctx.Slice_length)
    }
  }
  describe("End") {
    it("interval") {
      ctx.emit("Interval_end", ctx.Interval_end)
    }
    it("slice") {
      ctx.emit("Slice_end", ctx.Slice_end)
    }
  }
  it("Interval_shift") {
    ctx.emit("Interval_shift", ctx.Interval_shift)
  }
  it("Slice_shift") {
    ctx.emit("Slice_shift", ctx.Slice_shift)
  }
}

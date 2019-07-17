package scalan.primitives

import scalan.{Scalan, Base}

trait UnBinOps extends Base { self: Scalan =>

  class UnOp[A, R](val opName: String, val applySeq: A => R)(implicit val eResult: Elem[R]) {
    override def toString = opName

    def apply(arg: Rep[A]) = applyUnOp(this, arg)

    def shouldPropagate(arg: A) = true
  }

  class BinOp[A, R](val opName: String, val applySeq: (A, A) => R)(implicit val eResult: Elem[R]) {
    override def toString = opName

    def apply(lhs: Rep[A], rhs: Rep[A]) = applyBinOp(this, lhs, rhs)
    def applyLazy(lhs: Rep[A], rhs: Rep[Thunk[A]]) = applyBinOpLazy(this, lhs, rhs)

    // ideally shouldn't be necessary, but
    // we curently can't handle division by zero properly
    def shouldPropagate(lhs: A, rhs: A) = true
  }

  type EndoUnOp[A] = UnOp[A, A]
  type EndoBinOp[A] = BinOp[A, A]

  case class ApplyUnOp[A, R](op: UnOp[A, R], arg: Rep[A]) extends BaseDef[R]()(op.eResult) {
    override def toString = s"$op($arg)"
    override def transform(t: Transformer): Def[R] = ApplyUnOp[A,R](op, t(arg))
  }

  case class ApplyBinOp[A, R](op: BinOp[A, R], lhs: Rep[A], rhs: Rep[A]) extends BaseDef[R]()(op.eResult) {
    override def toString = s"$op($lhs, $rhs)"
    override def transform(t: Transformer): Def[R] = ApplyBinOp[A,R](op, t(lhs), t(rhs))
  }
  case class ApplyBinOpLazy[A, R](op: BinOp[A, R], lhs: Rep[A], rhs: Rep[Thunk[A]]) extends BaseDef[R]()(op.eResult) {
    override def toString = s"$lhs $op { $rhs }"
    override def transform(t: Transformer): Def[R] = ApplyBinOpLazy[A,R](op, t(lhs), t(rhs))
  }

  def applyUnOp[A, R](op: UnOp[A, R], arg: Rep[A]): Rep[R] = ApplyUnOp(op, arg)

  def applyBinOp[A, R](op: BinOp[A, R], lhs: Rep[A], rhs: Rep[A]): Rep[R] = ApplyBinOp(op, lhs, rhs)
  def applyBinOpLazy[A, R](op: BinOp[A, R], lhs: Rep[A], rhs: Rep[Thunk[A]]): Rep[R] = ApplyBinOpLazy(op, lhs, rhs)

  // allows use of context bounds in classes extending UnOp/BinOp.
  // Note that this must be overridden if some transformation _is_ needed (i.e. if the class contains Rep[_] somewhere)
  override protected def transformProductParam(x: Any, t: Transformer) = x match {
    case (_: UnOp[_, _]) | (_: BinOp[_, _]) => x
    case _ => super.transformProductParam(x, t)
  }
}
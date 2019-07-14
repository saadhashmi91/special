package scalan.primitives

import scalan.{Base, Scalan}

trait TypeSum extends Base { self: Scalan =>
  import IsoUR._
  import Converter._

  trait SumOps[A, B] {
    def isLeft: Rep[Boolean]

    def isRight: Rep[Boolean]

    def fold[R: Elem](l: Rep[A] => Rep[R], r: Rep[B] => Rep[R]): Rep[R]

    def foldBy[R](l: Rep[A => R], r: Rep[B => R]): Rep[R]

    def mapSum[C: Elem, D: Elem](fl: Rep[A] => Rep[C], fr: Rep[B] => Rep[D]): Rep[C | D]

    def mapSumBy[C, D](fl: Rep[A => C], fr: Rep[B => D]): Rep[C | D]
  }

  implicit class RepExtensionsForSum[A](x: Rep[A]) {
    def asLeft[B: Elem]: Rep[A | B] = mkLeft[A, B](x)

    def asRight[B: Elem]: Rep[B | A] = mkRight[B, A](x)
  }

  implicit class JoinSumOps[A](sum: Rep[A | A]) {
    def joinSum: Rep[A] = {
      implicit val eA = sum.elem.eLeft
      sum.foldBy(identityFun, identityFun)
    }
  }

  implicit class OptionOps[A](opt: ROptional[A]) {
    implicit def eA: Elem[A] = opt.elem.eRight
    def map[B](f: Rep[A] => Rep[B]): ROptional[B] =
      mapBy(fun(f))

    def mapBy[B](f: Rep[A => B]): ROptional[B] =
      opt.mapSumBy(identityFun, f)

    def flatMap[B](f: Rep[A] => ROptional[B]): ROptional[B] =
      flatMapBy(fun(f))

    def flatMapBy[B](f: Rep[A => SOptional[B]]): ROptional[B] = {
      implicit val eB = f.elem.eRange.eRight
      opt.foldBy(constFun(SOptional.none[B]), f)
    }

    def getOrElse[B >: A](default: Rep[B]): Rep[B] =
      opt.foldBy(constFun(default), upcastFun[A,B])

    def isEmpty = opt.isLeft

    def isDefined = opt.isRight
  }

  type SOptional[A] = Unit | A
  type ROptional[A] = Rep[SOptional[A]]

  object SOptional {
    def none[A: Elem] = mkLeft[Unit, A](())

    def some[A](x: Rep[A]) = mkRight[Unit, A](x)
  }

  // TODO used by generated code; ideally should be unnecessary
  def sOptionElement[A: Elem] = element[SOptional[A]]

  case class SLeft[A, B](left: Rep[A])(implicit val eRight: Elem[B]) extends BaseDef[A | B]()(sumElement(left.elem, eRight)) {
    override def transform(t: Transformer): Def[A | B] = SLeft(t(left))(eRight)
  }

  case class SRight[A, B](right: Rep[B])(implicit val eLeft: Elem[A]) extends BaseDef[A | B]()(sumElement(eLeft, right.elem)) {
    override def transform(t: Transformer): Def[A | B] = SRight(t(right))(eLeft)
  }

  def mkLeft[A, B: Elem](a: Rep[A]): Rep[A | B] = SLeft[A, B](a)(element[B])

  def mkRight[A: Elem, B](b: Rep[B]): Rep[A | B] = SRight[A, B](b)(element[A])

  case class SumFold[A, B, R](sum: Rep[A | B], left: Rep[A => R], right: Rep[B => R])
    extends BaseDef[R]()(left.elem.eRange) {
    override def transform(t: Transformer): Def[R] = SumFold(t(sum), t(left), t(right))
  }

  case class SumMap[A, B, C, D](sum: Rep[A | B], left: Rep[A => C], right: Rep[B => D])
    extends BaseDef[C | D]()(sumElement(left.elem.eRange, right.elem.eRange)) {
    override def transform(t: Transformer): Def[C | D] = SumMap(t(sum), t(left), t(right))
  }

  class SumOpsExp[A, B](s: Rep[A | B]) extends SumOps[A, B] {
    implicit def eLeft: Elem[A] = s.elem.eLeft

    implicit def eRight: Elem[B] = s.elem.eRight

    def fold[R: Elem](l: Rep[A] => Rep[R], r: Rep[B] => Rep[R]): Rep[R] = foldBy(fun(l), fun(r))

    def foldBy[R](l: Rep[A => R], r: Rep[B => R]): Rep[R] = SumFold(s, l, r)

    def mapSum[C: Elem, D: Elem](fl: Rep[A] => Rep[C], fr: Rep[B] => Rep[D]) = mapSumBy(fun(fl), fun(fr))

    def mapSumBy[C, D](l: Rep[A => C], r: Rep[B => D]): Rep[C | D] = SumMap(s, l, r)

    def isLeft = foldBy(constFun(true), constFun(false))

    def isRight = foldBy(constFun(false), constFun(true))
  }

  implicit def pimpSum[A, B](s: Rep[A | B]): SumOps[A, B] = new SumOpsExp[A, B](s)

  object IsLeft {
    def unapply(b: Def[_]): Option[Rep[_ | _]] = b match {
      case SumFold(sum, Def(VeryConstantLambda(true)), Def(VeryConstantLambda(false))) => Some(sum)
      case _ => None
    }
  }

  object IsRight {
    def unapply(b: Def[_]): Option[Rep[_ | _]] = b match {
      case SumFold(sum, Def(VeryConstantLambda(false)), Def(VeryConstantLambda(true))) => Some(sum)
      case _ => None
    }
  }

  object IsJoinSum {
    def unapply[T](d: Def[T]): Option[Rep[Source] forSome {type Source}] = d match {
      case SumFold(source, Def(IdentityLambda()), Def(IdentityLambda())) => Some(source)
      case _ => None
    }
  }

  object IsSumMapLambda {
    def unapply[A, B](lam: Lambda[A, B]): Option[SumMap[_, _, _, _]] = lam.y match {
      case Def(m: SumMap[_, _, _, _]) if lam.x == m.sum => Some(m)
      case _ => None
    }
  }

  def liftFromSumFold[T1, T2, A, B](sum: Rep[T1 | T2], left: Rep[T1 => B], right: Rep[T2 => B],
                                    iso: Iso[A, B]): Rep[B] = {
    implicit val eA = iso.eFrom
    val res = sum.foldBy(iso.fromFun << left, iso.fromFun << right)
    iso.to(res)
  }

  def liftFromSumFold[T1, T2, A, B, C, D](sum: Rep[T1 | T2], left: Rep[T1 => C], right: Rep[T2 => D],
                                          iso1: Iso[A, C], iso2: Iso[B, D],
                                          toD: Conv[C, D], toC: Conv[D, C]): Rep[C] = {
    implicit val eA = iso1.eFrom
    val res = sum.foldBy(iso1.fromFun << left, iso1.fromFun << toC.convFun << right)
    iso1.to(res)
  }

  case class HasArg(predicate: Rep[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Def[T]] = {
      val args = d.deps
      if (args.exists(predicate)) Some(d) else None
    }
  }

  case class FindArg(predicate: Rep[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Rep[_]] = {
      val args = d.deps
      for { a <- args.find(predicate) } yield a
    }
  }

  val FindFoldArg = FindArg {
    case Def(_: SumFold[_, _, _]) => true
    case _ => false
  }

  override def toRep[A](x: A)(implicit eA: Elem[A]):Rep[A] = eA match {
    case se: SumElem[a, b] =>
      val x1 = x.asInstanceOf[a | b]
      implicit val eA = se.eLeft
      implicit val eB = se.eRight
      x1.fold(l => SLeft[a, b](l), r => SRight[a, b](r))
    case _ =>
      super.toRep(x)(eA)
  }

  override def rewriteDef[T](d: Def[T]) = d match {
    case SumFold(sum, Def(ConstantLambda(l)), Def(ConstantLambda(r))) if l == r =>
      l

    // Rule: fold(s, l, r)._1 ==> fold(s, x => l(x)._1, y => r(y)._1)
    case First(Def(foldD: SumFold[a, b, (T, r2)] @unchecked)) =>
      implicit val eRes = foldD.selfType
      implicit val eT = eRes.eFst
      foldD.sum.foldBy(foldD.left >> fun(_._1), foldD.right >> fun(_._1))

    // Rule: fold(s, l, r)._2 ==> fold(s, x => l(x)._2, y => r(y)._2)
    case Second(Def(foldD: SumFold[a, b, (r1, T)] @unchecked)) =>
      implicit val eRes = foldD.selfType
      implicit val eT = eRes.eSnd
      foldD.sum.foldBy(foldD.left >> fun(_._2), foldD.right >> fun(_._2))

    case SumMap(Def(SRight(x)), f: RFunc[_, b]@unchecked, g) =>
      g(x).asRight[b](f.elem.eRange)

    case SumMap(Def(SLeft(x)), f, g: RFunc[_, d]@unchecked) =>
      f(x).asLeft[d](g.elem.eRange)

    case m1@SumMap(Def(f: SumFold[a0, b0, _]), left, right) =>
      f.sum.foldBy(left << f.left, right << f.right)

    case m1@SumMap(Def(m2: SumMap[a0, b0, a1, b1]), left, right) =>
      m2.sum.mapSumBy(left << m2.left, right << m2.right)

    case f@SumFold(Def(m: SumMap[a0, b0, a, b]), left, right) =>
      m.sum.foldBy(left << m.left, right << m.right)

    case foldD: SumFold[a, b, T] => foldD.sum match {

      // Rule: fold(SumView(source, iso1, iso2), l, r) ==> fold(source, iso1.to >> l, iso2.to >> r)
      case Def(view: SumView[a1, a2, b1, b2]) =>
        view.source.foldBy(asRep[b1 => T](foldD.left) << view.iso1.toFun, asRep[b2 => T](foldD.right) << view.iso2.toFun)

      // Rule: fold(fold(sum, id, id), l, r) ==> fold(sum, x => fold(x, l, r), y => fold(y, l, r))
      case Def(join@IsJoinSum(sum)) =>
        val source = asRep[(a | b) | (a | b)](sum)
        implicit val eRes: Elem[a | b] = source.elem.eLeft
        implicit val eT = foldD.left.elem.eRange
        val f1 = fun { x: Rep[a | b] => x.foldBy(foldD.left, foldD.right) }
        source.foldBy(f1, f1)

      // Rule: fold(Left(left), l, r) ==> l(left)
      case Def(SLeft(left: Rep[a])) =>
        implicit val eLeft = left.elem
        foldD.left(left)

      // Rule: fold(Right(right), l, r) ==> r(right)
      case Def(SRight(right: Rep[a])) =>
        implicit val eRight = right.elem
        foldD.right(right)

      case _ => super.rewriteDef(d)
    }

    case call@MethodCall(Def(foldD@SumFold(sum, left, right)), m, args, neverInvoke) => {
      implicit val resultElem: Elem[T] = d.selfType
      def copyMethodCall(newReceiver: Sym) =
        asRep[T](mkMethodCall(newReceiver, m, args, neverInvoke, isAdapterCall = false, resultElem))

      sum.fold(
        a => copyMethodCall(left(a)),
        b => copyMethodCall(right(b))
      )
    }

    case _ => super.rewriteDef(d)
  }

  override def rewriteViews[T](d: Def[T]) = d match {
    // Rule: Left[A,B](V(a, iso)) ==> V(Left(a), SumIso(iso, iso[B]))
    case l@SLeft(HasViews(a, iso: Iso[a1, b1])) =>
      val eR = l.eRight
      getIsoByElem(eR) match {
        case iso1: Iso[a2, b2] =>
          SumView(asRep[a1](a).asLeft(iso1.eFrom))(iso, iso1).self
      }

    // Rule: Right[A,B](V(a, iso)) ==> V(Right(a), SumIso(iso[A], iso))
    case r@SRight(HasViews(a, iso: Iso[a1, b1])) =>
      val eL = r.eLeft
      getIsoByElem(eL) match {
        case iso1: Iso[a2, b2] =>
          SumView(asRep[a1](a).asRight(iso1.eFrom))(iso1, iso).self
      }

    case foldD@SumFold(sum,
          LambdaResultHasViews(left, iso1: Iso[a, c]),
          LambdaResultHasViews(right, iso2: Iso[_, _])) if iso1 == iso2 =>
      val newFold = liftFromSumFold(sum, left, right, iso1)
      newFold

    // Rule:
    case call@MethodCall(
          Def(foldD @ SumFold(sum,
          LambdaResultHasViews(left, iso1: Iso[a, c]),
          LambdaResultHasViews(right, iso2: Iso[_, _]))), m, args, neverInvoke) if iso1 == iso2 =>
      val newFold = liftFromSumFold(foldD.sum, foldD.left, foldD.right, iso1.asIso[a,Any])
      mkMethodCall(newFold, m, args, neverInvoke, isAdapterCall = false, call.selfType)

    case _ => super.rewriteViews(d)
  }
}

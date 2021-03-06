package scalan.primitives

import scalan.{ScalanEx, BaseEx}

trait TypeSumEx extends BaseEx { self: ScalanEx =>
  import IsoUR._
  import Converter._

  override def toRep[A](x: A)(implicit eA: Elem[A]):Ref[A] = eA match {
    case se: SumElem[a, b] =>
      val x1 = x.asInstanceOf[a | b]
      implicit val eA = se.eLeft
      implicit val eB = se.eRight
      x1.fold(l => SLeft[a, b](l, eB), r => SRight[a, b](r, eA))
    case _ =>
      super.toRep(x)(eA)
  }

  object IsLeft {
    def unapply(b: Def[_]): Option[Ref[_ | _]] = b match {
      case SumFold(sum, Def(VeryConstantLambda(true)), Def(VeryConstantLambda(false))) => Some(sum)
      case _ => None
    }
  }

  object IsRight {
    def unapply(b: Def[_]): Option[Ref[_ | _]] = b match {
      case SumFold(sum, Def(VeryConstantLambda(false)), Def(VeryConstantLambda(true))) => Some(sum)
      case _ => None
    }
  }

  object IsJoinSum {
    def unapply[T](d: Def[T]): Option[Ref[Source] forSome {type Source}] = d match {
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

  def liftFromSumFold[T1, T2, A, B](sum: Ref[T1 | T2], left: Ref[T1 => B], right: Ref[T2 => B],
                                    iso: Iso[A, B]): Ref[B] = {
    implicit val eA = iso.eFrom
    val res = sum.foldBy(iso.fromFun << left, iso.fromFun << right)
    iso.to(res)
  }

  def liftFromSumFold[T1, T2, A, B, C, D](sum: Ref[T1 | T2], left: Ref[T1 => C], right: Ref[T2 => D],
                                          iso1: Iso[A, C], iso2: Iso[B, D],
                                          toD: Conv[C, D], toC: Conv[D, C]): Ref[C] = {
    implicit val eA = iso1.eFrom
    val res = sum.foldBy(iso1.fromFun << left, iso1.fromFun << toC.convFun << right)
    iso1.to(res)
  }

  case class HasArg(predicate: Ref[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Def[T]] = {
      val args = d.deps
      if (args.exists(predicate)) Some(d) else None
    }
  }

  case class FindArg(predicate: Ref[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Ref[_]] = {
      val args = d.deps
      for { a <- args.find(predicate) } yield a
    }
  }

  val FindFoldArg = FindArg {
    case Def(_: SumFold[_, _, _]) => true
    case _ => false
  }


  override def rewriteDef[T](d: Def[T]) = d match {
    case SumFold(sum, Def(ConstantLambda(l)), Def(ConstantLambda(r))) if l == r =>
      l

    // Rule: fold(s, l, r)._1 ==> fold(s, x => l(x)._1, y => r(y)._1)
    case First(Def(foldD: SumFold[a, b, (T, r2)] @unchecked)) =>
      implicit val eRes = foldD.resultType
      implicit val eT = eRes.eFst
      foldD.sum.foldBy(foldD.left >> fun(_._1), foldD.right >> fun(_._1))

    // Rule: fold(s, l, r)._2 ==> fold(s, x => l(x)._2, y => r(y)._2)
    case Second(Def(foldD: SumFold[a, b, (r1, T)] @unchecked)) =>
      implicit val eRes = foldD.resultType
      implicit val eT = eRes.eSnd
      foldD.sum.foldBy(foldD.left >> fun(_._2), foldD.right >> fun(_._2))

    case SumMap(Def(SRight(x, _)), f: RFunc[_, b]@unchecked, g) =>
      g(x).asRight[b](f.elem.eRange)

    case SumMap(Def(SLeft(x, _)), f, g: RFunc[_, d]@unchecked) =>
      f(x).asLeft[d](g.elem.eRange)

    case m1@SumMap(Def(f: SumFold[a0, b0, _]), left, right) =>
      f.sum.foldBy(left << f.left, right << f.right)

    case m1@SumMap(Def(m2: SumMap[a0, b0, a1, b1]), left, right) =>
      m2.sum.mapSumBy(left << m2.left, right << m2.right)

    case f@SumFold(Def(m: SumMap[a0, b0, a, b]), left, right) =>
      m.sum.foldBy(left << m.left, right << m.right)

    case foldD: SumFold[a, b, T]@unchecked => foldD.sum match {

      // Rule: fold(SumView(source, iso1, iso2), l, r) ==> fold(source, iso1.to >> l, iso2.to >> r)
      case Def(view: SumView[a1, a2, b1, b2]) =>
        view.source.foldBy(asRep[b1 => T](foldD.left) << view.iso1.toFun, asRep[b2 => T](foldD.right) << view.iso2.toFun)

      // Rule: fold(fold(sum, id, id), l, r) ==> fold(sum, x => fold(x, l, r), y => fold(y, l, r))
      case Def(join@IsJoinSum(sum)) =>
        val source = asRep[(a | b) | (a | b)](sum)
        implicit val eRes: Elem[a | b] = source.elem.eLeft
        implicit val eT = foldD.left.elem.eRange
        val f1 = fun { x: Ref[a | b] => x.foldBy(foldD.left, foldD.right) }
        source.foldBy(f1, f1)

      // Rule: fold(Left(left), l, r) ==> l(left)
      case Def(SLeft(left: Ref[a], _)) =>
        implicit val eLeft = left.elem
        foldD.left(left)

      // Rule: fold(Right(right), l, r) ==> r(right)
      case Def(SRight(right: Ref[a], _)) =>
        implicit val eRight = right.elem
        foldD.right(right)

      case _ => super.rewriteDef(d)
    }

    case call@MethodCall(Def(foldD@SumFold(sum, left, right)), m, args, neverInvoke) => {
      implicit val resultElem: Elem[T] = d.resultType
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
    case l@SLeft(HasViews(a, iso: Iso[a1, b1]), _) =>
      val eR = l.eRight
      getIsoByElem(eR) match {
        case iso1: Iso[a2, b2] =>
          SumView(asRep[a1](a).asLeft(iso1.eFrom))(iso, iso1).self
      }

    // Rule: Right[A,B](V(a, iso)) ==> V(Right(a), SumIso(iso[A], iso))
    case r@SRight(HasViews(a, iso: Iso[a1, b1]), _) =>
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
      mkMethodCall(newFold, m, args, neverInvoke, isAdapterCall = false, call.resultType)

    case _ => super.rewriteViews(d)
  }
}

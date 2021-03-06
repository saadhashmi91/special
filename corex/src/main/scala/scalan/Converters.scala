package scalan

import OverloadHack.Overloaded2
import scalan.primitives.TypeSum

trait Converters extends ViewsModule with TypeSum { self: Scalan with ConvertersModule =>
  import Converter._
  type Conv[T,R] = Ref[Converter[T,R]]
  trait Converter[T,R] extends Def[Converter[T,R]] {
    implicit def eT: Elem[T]
    implicit def eR: Elem[R]
    def convFun: Ref[T => R] = defaultConvFun
    private[this] lazy val defaultConvFun: Ref[T => R] = fun { apply(_: Ref[T]) }
    def apply(x: Ref[T]): Ref[R]
    def isIdentity: Boolean = false
    override def toString: String = s"${eT.name} --> ${eR.name}"
  }
  trait ConverterCompanion

  @Isospec
  abstract class IdentityConv[A](implicit val eT: Elem[A]) extends Converter[A, A] {
    def eR: Elem[A] = eT
    def apply(x: Ref[A]) = x
    override val convFun = identityFun[A]
    override def isIdentity = true
    override def equals(other: Any) = other match {
      case i: Converters#IdentityConv[_] => (this eq i) || (eT == i.eT)
      case _ => false
    }
  }

  implicit class ConvOps[A,B](c: Conv[A,B]) {
    def >>[B1 >: B, C](c2: Conv[B1,C]): Conv[A,C] = composeConv(c2, c)
    def >>[B1 >: B, C](f: Ref[B1 => C])(implicit o2: Overloaded2): Ref[A => C] = {
      compose(f, asRep[A => B1](funcFromConv(c)))
    }
  }
  implicit class AnyConvOps(c: Conv[_, _]) {
    def asConv[C,D] = c.asInstanceOf[Conv[C,D]]
  }

  @Isospec
  abstract class BaseConverter[T,R](override val convFun: Ref[T => R])
    extends Converter[T,R] {
    implicit def eT: Elem[T]
    implicit def eR: Elem[R]
    def apply(x: Ref[T]): Ref[R] = convFun(x)
    override def equals(other: Any): Boolean = other match {
      case c: Converters#BaseConverter[_, _] => eT == c.eT && eR == c.eR && convFun == c.convFun
      case _ => false
    }
  }
  trait BaseConverterCompanion

  @Isospec
  abstract class PairConverter[A1, A2, B1, B2]
    (val conv1: Conv[A1, B1], val conv2: Conv[A2, B2])
    extends Converter[(A1, A2), (B1, B2)] {
    def eA1: Elem[A1]; def eA2: Elem[A2]; def eB1: Elem[B1]; def eB2: Elem[B2]
    def apply(x: Ref[(A1,A2)]) = { val Pair(a1, a2) = x; Pair(conv1(a1), conv2(a2)) }
    override def isIdentity = conv1.isIdentity && conv2.isIdentity
  }
  trait PairConverterCompanion

  @Isospec
  abstract class SumConverter[A1, A2, B1, B2]
    (val conv1: Conv[A1, B1], val conv2: Conv[A2, B2])
    extends Converter[(A1 | A2), (B1 | B2)] {
    def eA1: Elem[A1]; def eA2: Elem[A2]; def eB1: Elem[B1]; def eB2: Elem[B2]
    def apply(x: Ref[(A1|A2)]) = { x.mapSumBy(conv1.convFun, conv2.convFun) }
    override def isIdentity = conv1.isIdentity && conv2.isIdentity
  }
  trait SumConverterCompanion

  @Isospec
  abstract class ComposeConverter[A, B, C](val conv2: Conv[B, C], val conv1: Conv[A, B])
    extends Converter[A, C] {
    val eT: Elem[A] = conv1.eT
    val eR: Elem[C] = conv2.eR
    def apply(a: Ref[A]) = conv2.apply(conv1.apply(a))
    override def isIdentity = conv1.isIdentity && conv2.isIdentity
    override def equals(other: Any) = other match {
      case i: Converters#ComposeConverter[_, _, _] => (this eq i) || (conv1 == i.conv1 && conv2 == i.conv2)
      case _ => false
    }
  }

  @Isospec
  abstract class FunctorConverter[A,B,F[_]]
      (val itemConv: Conv[A, B])
      (implicit val F: Functor[F])
    extends Converter[F[A], F[B]] {
    def apply(xs: Ref[F[A]]): Ref[F[B]] = F.map(xs){ x => itemConv(x) }
    def eA: Elem[A]; def eB: Elem[B]
    lazy val eT: Elem[F[A]] = F.lift(eA)
    lazy val eR: Elem[F[B]] = F.lift(eB)
    override def isIdentity = itemConv.isIdentity
    override def equals(other: Any): Boolean = other match {
      case c: Converters#FunctorConverter[_, _, _] => eT == c.eT && eR == c.eR && itemConv == c.itemConv
      case _ => false
    }
  }
  trait FunctorConverterCompanion

  @Isospec
  abstract class NaturalConverter[A,F[_],G[_]]
      (override val convFun: Ref[F[A] => G[A]])
      (implicit val eA: Elem[A], val cF: Cont[F], val cG: Cont[G])
    extends Converter[F[A], G[A]] {
    def apply(xs: Ref[F[A]]): Ref[G[A]] = convFun(xs)

    val eT = cF.lift(eA)
    val eR = cG.lift(eA)
    override def equals(other: Any): Boolean = other match {
      case c: Converters#NaturalConverter[_, _, _] => eT == c.eT && eR == c.eR && convFun == c.convFun
      case _ => false
    }
  }

  @Isospec
  abstract class ConverterIso[A, B](val convTo: Conv[A,B], val convFrom: Conv[B,A])
      extends IsoUR[A,B] {
    def eA: Elem[A]; def eB: Elem[B]
    def eFrom: Elem[A] = eA
    def eTo: Elem[B] = eB
    def to(a: Ref[A]) = convTo(a)
    def from(b: Ref[B]) = convFrom(b)
    override lazy val toFun = convTo.convFun
    override lazy val fromFun = convFrom.convFun
    override def isIdentity = false
    override def equals(other: Any) = other match {
      case i: Converters#ConverterIso[_, _] => (this eq i) || (convTo == i.convTo && convFrom == i.convFrom)
      case _ => false
    }
  }
}

trait ConvertersModule extends impl.ConvertersDefs { self: Scalan =>
  import IsoUR._
  import Iso1UR._
  import IdentityIso._
  import PairIso._
  import AbsorbFirstUnitIso._
  import AbsorbSecondUnitIso._
  import SumIso._
  import ComposeIso._
  import FuncIso._
  import ThunkIso._
  import Converter._
  import IdentityConv._
  import BaseConverter._
  import PairConverter._
  import SumConverter._
  import ComposeConverter._
  import FunctorConverter._
  import NaturalConverter._
  import ConverterIso._

  def identityConv[A](implicit elem: Elem[A]): Conv[A, A] = RIdentityConv[A]()(elem)

  def baseConv[T,R](f: Ref[T => R]): Conv[T,R] = RBaseConverter(f)
  def funcFromConv[T,R](c: Conv[T,R]): Ref[T => R] = c.convFun

  def pairConv[A1, A2, B1, B2](conv1: Conv[A1, B1], conv2: Conv[A2, B2]): Conv[(A1, A2), (B1, B2)] =
    RPairConverter[A1, A2, B1, B2](conv1, conv2)
    
  def composeConv[A, B, B1 >: B, C](c2: Conv[B1, C], c1: Conv[A, B]): Conv[A, C] = {
    if (c2.isIdentity)
      c1
    else if (c1.isIdentity)
      c2
    else
      (c2, c1) match {
        case (Def(conv2d: PairConverter[b1, b2, c1, c2]), Def(conv1d: PairConverter[a1, a2, _, _])) =>
          val composedConv1 = composeConv(conv2d.conv1, conv1d.conv1.asInstanceOf[Conv[a1, b1]])
          val composedConv2 = composeConv(conv2d.conv2, conv1d.conv2.asInstanceOf[Conv[a2, b2]])
          pairConv(composedConv1, composedConv2)
        case _ =>
          RComposeConverter[A, B1, C](c2, c1.asConv[A,B1])
      }
  }.asInstanceOf[Conv[A, C]]

  object HasConv {
    def unapply[A,B](elems: (Elem[A], Elem[B])): Option[Conv[A,B]] = getConverter(elems._1, elems._2)
  }

  object IsConvertible {
    def unapply[A,B](elems: (Elem[A], Elem[B])): Option[(Conv[A,B], Conv[B,A])] =
      for {
        c1 <- HasConv.unapply(elems)
        c2 <- HasConv.unapply(elems.swap)
      }
        yield (c1, c2)
  }

  def getConverterFrom[TData, TClass, E](eEntity: EntityElem[E], eClass: ConcreteElem[TData, TClass]): Option[Conv[E, TClass]] = {
    try {
      val convFun: Ref[E => TClass] =
        fun({ x: Ref[E] => eClass.convert(asRep[Def[_]](x))})(Lazy(eEntity))
      Some(RBaseConverter(convFun))
    }
    catch {
      case e: RuntimeException => None
    }
  }

  def getConverter[A,B](eA: Elem[A], eB: Elem[B]): Option[Conv[A,B]] = {
    (eA, eB) match {
      case (e1, e2) if e1 == e2 =>
        implicit val ea = e1
        Some(identityConv[A].asConv[A,B])
      case (pA: PairElem[a1,a2], pB: PairElem[b1,b2]) =>
        for {
          c1 <- getConverter(pA.eFst, pB.eFst)
          c2 <- getConverter(pA.eSnd, pB.eSnd)
        }
        yield pairConv(c1, c2)
      case (pA: SumElem[a1,a2], pB: SumElem[b1,b2]) =>
        implicit val ea1 = pA.eLeft
        implicit val eb1 = pB.eLeft
        implicit val ea2 = pA.eRight
        implicit val eb2 = pB.eRight
        for {
          c1 <- getConverter(ea1, eb1)
          c2 <- getConverter(ea2, eb2)
        }
        yield RSumConverter(c1, c2)
      case (e1: EntityElem1[a1,to1,_], e2: EntityElem1[a2,to2,_])
        if e1.cont.name == e2.cont.name && e1.cont.isFunctor =>
        implicit val ea1 = e1.eItem
        implicit val ea2 = e2.eItem
        type F[T] = T
        val F = e1.cont.asInstanceOf[Functor[F]]
        for { c <- getConverter(ea1, ea2) }
          yield asRep[Converter[A,B]](RFunctorConverter(c)(F))
      case (eEntity: EntityElem[_], eClass: ConcreteElem[tData,tClass]) =>
        val convOpt = getConverterFrom(eEntity, eClass)
        convOpt
      case (eClass: ConcreteElem[tData,tClass], eEntity: EntityElem[_]) if eClass <:< eEntity =>
        Some(asRep[Converter[A,B]](RBaseConverter(identityFun(eClass))))
      case _ => None
    }
  }

  def converterIso[A, B](convTo: Conv[A,B], convFrom: Conv[B,A]): Iso[A,B] = {
    val convToElem = convTo.elem.asInstanceOf[ConverterElem[A, B, _]]
    RConverterIso[A, B](convTo, convFrom)
  }

  def convertBeforeIso[A, B, C](convTo: Conv[A,B], convFrom: Conv[B,A], iso0: Iso[B,C]): Iso[A, C] = composeIso(iso0, converterIso(convTo, convFrom))

  def convertAfterIso[A,B,C](iso0: Iso[A,B], convTo: Conv[B,C], convFrom: Conv[C,B]): Iso[A, C] = composeIso(converterIso(convTo, convFrom), iso0)

  def unifyIsos[A,B,C,D](iso1: Iso[A,C], iso2: Iso[B,D],
                         toD: Conv[C,D], toC: Conv[D,C]): (Iso[A,C], Iso[B,C]) = {
    val ea = iso1.eFrom
    val eb = iso2.eFrom
    implicit val ec = iso1.eTo
    val (i1, i2) =
      if (ec == iso2.eTo)
        (iso1, iso2.asInstanceOf[Iso[B,C]])
      else
        (iso1, convertAfterIso(iso2, toC, toD))
    (i1, i2)
  }

}

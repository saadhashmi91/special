package scalan.common

import scalan._
import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeTag}
import scalan.meta.ScalanAst._
import scala.collection.mutable.WrappedArray

package impl {
// Abs -----------------------------------
trait KindsDefs extends scalan.Scalan with Kinds {
  self: KindsModule =>
import IsoUR._
import Converter._
import Kind._
import Bind._
import Return._

object Kind extends EntityObject("Kind") {
  private val KindClass = classOf[Kind[C, _] forSome {type C[_]}]

  // entityAdapter for Kind trait
  case class KindAdapter[F[_], A](source: Rep[Kind[F, A]])
      extends Kind[F, A]
      with Def[Kind[F, A]] {
    implicit lazy val cF = source.elem.typeArgs("F")._1.asCont[F];
implicit lazy val eA = source.elem.typeArgs("A")._1.asElem[A]

    val selfType: Elem[Kind[F, A]] = element[Kind[F, A]]
    override def transform(t: Transformer) = KindAdapter[F, A](t(source))
  }

  // entityProxy: single proxy for each type family
  val createKindAdapter = (x: Rep[Kind[Array, Any]]) => KindAdapter(x)

  implicit def proxyKind[F[_], A](p: Rep[Kind[F, A]]): Kind[F, A] = {
    val sym = p.asInstanceOf[SingleSym[Kind[F, A]]]
    sym.getAdapter(
      p.rhs.isInstanceOf[Kind[F, A]@unchecked],
      createKindAdapter.asInstanceOf[Rep[Kind[F, A]] => Kind[F, A]])
  }

  // familyElem
  class KindElem[F[_], A, To <: Kind[F, A]](implicit _cF: Cont[F], _eA: Elem[A])
    extends EntityElem[To] {
    def cF = _cF
    def eA = _eA

    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("F" -> (cF -> scalan.util.Invariant), "A" -> (eA -> scalan.util.Invariant))
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[Kind[F, A]] => convertKind(x) }
      tryConvert(element[Kind[F, A]], this, x, conv)
    }

    def convertKind(x: Rep[Kind[F, A]]): Rep[To] = {
      x.elem.asInstanceOf[Elem[_]] match {
        case _: KindElem[_, _, _] => asRep[To](x)
        case e => !!!(s"Expected $x to have KindElem[_, _, _], but got $e", x)
      }
    }
  }

  implicit def kindElement[F[_], A](implicit cF: Cont[F], eA: Elem[A]): Elem[Kind[F, A]] =
    cachedElemByClass(cF, eA)(classOf[KindElem[F, A, Kind[F, A]]])

  implicit case object KindCompanionElem extends CompanionElem[KindCompanionCtor]

  abstract class KindCompanionCtor extends CompanionDef[KindCompanionCtor] with KindCompanion {
    def selfType = KindCompanionElem
    override def toString = "Kind"
  }
  implicit def proxyKindCompanionCtor(p: Rep[KindCompanionCtor]): KindCompanionCtor =
    p.rhs.asInstanceOf[KindCompanionCtor]

  lazy val RKind: Rep[KindCompanionCtor] = new KindCompanionCtor {
    private val thisClass = classOf[KindCompanion]
  }

  object KindMethods {
    // WARNING: Cannot generate matcher for method `flatMap`: Method has function arguments f

    object mapBy {
      def unapply(d: Def[_]): Nullable[(Rep[Kind[F, A]], Rep[A => B]) forSome {type F[_]; type A; type B}] = d match {
        case MethodCall(receiver, method, args, _) if method.getName == "mapBy" && (receiver.elem.asInstanceOf[Elem[_]] match { case _: KindElem[_, _, _] => true; case _ => false }) =>
          val res = (receiver, args(0))
          Nullable(res).asInstanceOf[Nullable[(Rep[Kind[F, A]], Rep[A => B]) forSome {type F[_]; type A; type B}]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[(Rep[Kind[F, A]], Rep[A => B]) forSome {type F[_]; type A; type B}] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }
  }

  object KindCompanionMethods {
  }
} // of object Kind
  registerEntityObject("Kind", Kind)

object Return extends EntityObject("Return") {
  case class ReturnCtor[F[_], A]
      (override val a: Rep[A])(implicit cF: Cont[F])
    extends Return[F, A](a) with Def[Return[F, A]] {
    implicit lazy val eA = a.elem

    lazy val selfType = element[Return[F, A]]
    override def transform(t: Transformer) = ReturnCtor[F, A](t(a))(cF)
  }
  // elem for concrete class
  class ReturnElem[F[_], A](val iso: Iso[ReturnData[F, A], Return[F, A]])(implicit override val cF: Cont[F], override val eA: Elem[A])
    extends KindElem[F, A, Return[F, A]]
    with ConcreteElem[ReturnData[F, A], Return[F, A]] {
    override lazy val parent: Option[Elem[_]] = Some(kindElement(container[F], element[A]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("F" -> (cF -> scalan.util.Invariant), "A" -> (eA -> scalan.util.Invariant))
    override def convertKind(x: Rep[Kind[F, A]]) = // Converter is not generated by meta
!!!("Cannot convert from Kind to Return: missing fields List(a)")
  }

  // state representation type
  type ReturnData[F[_], A] = A

  // 3) Iso for concrete class
  class ReturnIso[F[_], A](implicit cF: Cont[F], eA: Elem[A])
    extends EntityIso[ReturnData[F, A], Return[F, A]] with Def[ReturnIso[F, A]] {
    override def transform(t: Transformer) = new ReturnIso[F, A]()(cF, eA)
    private lazy val _safeFrom = fun { p: Rep[Return[F, A]] => p.a }
    override def from(p: Rep[Return[F, A]]) =
      tryConvert[Return[F, A], A](eTo, eFrom, p, _safeFrom)
    override def to(p: Rep[A]) = {
      val a = p
      RReturn(a)
    }
    lazy val eFrom = element[A]
    lazy val eTo = new ReturnElem[F, A](self)
    lazy val selfType = new ReturnIsoElem[F, A](cF, eA)
    def productArity = 2
    def productElement(n: Int) = n match {
      case 0 => cF
      case 1 => eA
    }
  }
  case class ReturnIsoElem[F[_], A](cF: Cont[F], eA: Elem[A]) extends Elem[ReturnIso[F, A]] {
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("F" -> (cF -> scalan.util.Invariant), "A" -> (eA -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class ReturnCompanionCtor extends CompanionDef[ReturnCompanionCtor] with ReturnCompanion {
    def selfType = ReturnCompanionElem
    override def toString = "ReturnCompanion"

    @scalan.OverloadId("fromFields")
    def apply[F[_], A](a: Rep[A])(implicit cF: Cont[F]): Rep[Return[F, A]] =
      mkReturn(a)

    def unapply[F[_], A](p: Rep[Kind[F, A]]) = unmkReturn(p)
  }
  lazy val ReturnRep: Rep[ReturnCompanionCtor] = new ReturnCompanionCtor
  lazy val RReturn: ReturnCompanionCtor = proxyReturnCompanion(ReturnRep)
  implicit def proxyReturnCompanion(p: Rep[ReturnCompanionCtor]): ReturnCompanionCtor = {
    if (p.rhs.isInstanceOf[ReturnCompanionCtor])
      p.rhs.asInstanceOf[ReturnCompanionCtor]
    else
      proxyOps[ReturnCompanionCtor](p)
  }

  implicit case object ReturnCompanionElem extends CompanionElem[ReturnCompanionCtor]

  implicit def proxyReturn[F[_], A](p: Rep[Return[F, A]]): Return[F, A] = {
    if (p.rhs.isInstanceOf[Return[F, A]])
      p.rhs.asInstanceOf[Return[F, A]]
    else
      proxyOps[Return[F, A]](p)
  }

  implicit class ExtendedReturn[F[_], A](p: Rep[Return[F, A]])(implicit cF: Cont[F]) {
    def toData: Rep[ReturnData[F, A]] = {
      implicit val eA = p.a.elem
      isoReturn(cF, eA).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoReturn[F[_], A](implicit cF: Cont[F], eA: Elem[A]): Iso[ReturnData[F, A], Return[F, A]] =
    reifyObject(new ReturnIso[F, A]()(cF, eA))

  def mkReturn[F[_], A]
    (a: Rep[A])(implicit cF: Cont[F]): Rep[Return[F, A]] = {
    new ReturnCtor[F, A](a)
  }
  def unmkReturn[F[_], A](p: Rep[Kind[F, A]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: ReturnElem[F, A] @unchecked =>
      Some((asRep[Return[F, A]](p).a))
    case _ =>
      None
  }

    object ReturnMethods {
    // WARNING: Cannot generate matcher for method `flatMap`: Method has function arguments f
  }

  object ReturnCompanionMethods {
  }
} // of object Return
  registerEntityObject("Return", Return)

object Bind extends EntityObject("Bind") {
  case class BindCtor[F[_], S, B]
      (override val a: Rep[Kind[F, S]], override val f: Rep[S => Kind[F, B]])
    extends Bind[F, S, B](a, f) with Def[Bind[F, S, B]] {
    implicit lazy val cF = a.cF;
implicit lazy val eS = a.eA;
implicit lazy val eB = f.elem.eRange.typeArgs("A")._1.asElem[B]
    override lazy val eA: Elem[B] = eB
    lazy val selfType = element[Bind[F, S, B]]
    override def transform(t: Transformer) = BindCtor[F, S, B](t(a), t(f))
  }
  // elem for concrete class
  class BindElem[F[_], S, B](val iso: Iso[BindData[F, S, B], Bind[F, S, B]])(implicit override val cF: Cont[F], val eS: Elem[S], val eB: Elem[B])
    extends KindElem[F, B, Bind[F, S, B]]
    with ConcreteElem[BindData[F, S, B], Bind[F, S, B]] {
    override lazy val parent: Option[Elem[_]] = Some(kindElement(container[F], element[B]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("F" -> (cF -> scalan.util.Invariant), "S" -> (eS -> scalan.util.Invariant), "B" -> (eB -> scalan.util.Invariant))
    override def convertKind(x: Rep[Kind[F, B]]) = // Converter is not generated by meta
!!!("Cannot convert from Kind to Bind: missing fields List(a, f)")
  }

  // state representation type
  type BindData[F[_], S, B] = (Kind[F, S], S => Kind[F, B])

  // 3) Iso for concrete class
  class BindIso[F[_], S, B](implicit cF: Cont[F], eS: Elem[S], eB: Elem[B])
    extends EntityIso[BindData[F, S, B], Bind[F, S, B]] with Def[BindIso[F, S, B]] {
    override def transform(t: Transformer) = new BindIso[F, S, B]()(cF, eS, eB)
    private lazy val _safeFrom = fun { p: Rep[Bind[F, S, B]] => (p.a, p.f) }
    override def from(p: Rep[Bind[F, S, B]]) =
      tryConvert[Bind[F, S, B], (Kind[F, S], S => Kind[F, B])](eTo, eFrom, p, _safeFrom)
    override def to(p: Rep[(Kind[F, S], S => Kind[F, B])]) = {
      val Pair(a, f) = p
      RBind(a, f)
    }
    lazy val eFrom = pairElement(element[Kind[F, S]], element[S => Kind[F, B]])
    lazy val eTo = new BindElem[F, S, B](self)
    lazy val selfType = new BindIsoElem[F, S, B](cF, eS, eB)
    def productArity = 3
    def productElement(n: Int) = n match {
      case 0 => cF
      case 1 => eS
      case 2 => eB
    }
  }
  case class BindIsoElem[F[_], S, B](cF: Cont[F], eS: Elem[S], eB: Elem[B]) extends Elem[BindIso[F, S, B]] {
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("F" -> (cF -> scalan.util.Invariant), "S" -> (eS -> scalan.util.Invariant), "B" -> (eB -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class BindCompanionCtor extends CompanionDef[BindCompanionCtor] with BindCompanion {
    def selfType = BindCompanionElem
    override def toString = "BindCompanion"
    @scalan.OverloadId("fromData")
    def apply[F[_], S, B](p: Rep[BindData[F, S, B]]): Rep[Bind[F, S, B]] = {
      implicit val cF = p._1.cF;
implicit val eS = p._1.eA;
implicit val eB = p._2.elem.eRange.typeArgs("A")._1.asElem[B]
      isoBind[F, S, B].to(p)
    }

    @scalan.OverloadId("fromFields")
    def apply[F[_], S, B](a: Rep[Kind[F, S]], f: Rep[S => Kind[F, B]]): Rep[Bind[F, S, B]] =
      mkBind(a, f)

    def unapply[F[_], S, B](p: Rep[Kind[F, B]]) = unmkBind(p)
  }
  lazy val BindRep: Rep[BindCompanionCtor] = new BindCompanionCtor
  lazy val RBind: BindCompanionCtor = proxyBindCompanion(BindRep)
  implicit def proxyBindCompanion(p: Rep[BindCompanionCtor]): BindCompanionCtor = {
    if (p.rhs.isInstanceOf[BindCompanionCtor])
      p.rhs.asInstanceOf[BindCompanionCtor]
    else
      proxyOps[BindCompanionCtor](p)
  }

  implicit case object BindCompanionElem extends CompanionElem[BindCompanionCtor]

  implicit def proxyBind[F[_], S, B](p: Rep[Bind[F, S, B]]): Bind[F, S, B] = {
    if (p.rhs.isInstanceOf[Bind[F, S, B]])
      p.rhs.asInstanceOf[Bind[F, S, B]]
    else
      proxyOps[Bind[F, S, B]](p)
  }

  implicit class ExtendedBind[F[_], S, B](p: Rep[Bind[F, S, B]]) {
    def toData: Rep[BindData[F, S, B]] = {
      implicit val cF = p.a.cF;
implicit val eS = p.a.eA;
implicit val eB = p.f.elem.eRange.typeArgs("A")._1.asElem[B]
      isoBind(cF, eS, eB).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoBind[F[_], S, B](implicit cF: Cont[F], eS: Elem[S], eB: Elem[B]): Iso[BindData[F, S, B], Bind[F, S, B]] =
    reifyObject(new BindIso[F, S, B]()(cF, eS, eB))

  def mkBind[F[_], S, B]
    (a: Rep[Kind[F, S]], f: Rep[S => Kind[F, B]]): Rep[Bind[F, S, B]] = {
    new BindCtor[F, S, B](a, f)
  }
  def unmkBind[F[_], S, B](p: Rep[Kind[F, B]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: BindElem[F, S, B] @unchecked =>
      Some((asRep[Bind[F, S, B]](p).a, asRep[Bind[F, S, B]](p).f))
    case _ =>
      None
  }

    object BindMethods {
    // WARNING: Cannot generate matcher for method `flatMap`: Method has function arguments f1
  }

  object BindCompanionMethods {
  }
} // of object Bind
  registerEntityObject("Bind", Bind)

  registerModule(KindsModule)
}

object KindsModule extends scalan.ModuleInfo("scalan.common", "Kinds")
}

trait KindsModule extends scalan.common.impl.KindsDefs

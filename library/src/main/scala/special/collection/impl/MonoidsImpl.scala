package special.collection

import scalan._
import scala.reflect.runtime.universe._
import scala.reflect._

package impl {
// Abs -----------------------------------
trait MonoidsDefs extends scalan.Scalan with Monoids {
  self: Library =>
import IsoUR._
import Converter._
import Monoid._
import MonoidBuilder._

object Monoid extends EntityObject("Monoid") {
  // entityAdapter for Monoid trait
  case class MonoidAdapter[T](source: Rep[Monoid[T]])
      extends Monoid[T] with Def[Monoid[T]] {
    implicit lazy val eT = source.elem.typeArgs("T")._1.asElem[T]

    val selfType: Elem[Monoid[T]] = element[Monoid[T]]
    override def transform(t: Transformer) = MonoidAdapter[T](t(source))
    private val thisClass = classOf[Monoid[T]]

    def zero: Rep[T] = {
      asRep[T](mkMethodCall(source,
        thisClass.getMethod("zero"),
        List(),
        true, true, element[T]))
    }

    def plus(x: Rep[T], y: Rep[T]): Rep[T] = {
      asRep[T](mkMethodCall(source,
        thisClass.getMethod("plus", classOf[Sym], classOf[Sym]),
        List(x, y),
        true, true, element[T]))
    }

    def power(x: Rep[T], n: Rep[Int]): Rep[T] = {
      asRep[T](mkMethodCall(source,
        thisClass.getMethod("power", classOf[Sym], classOf[Sym]),
        List(x, n),
        true, true, element[T]))
    }
  }

  // entityProxy: single proxy for each type family
  implicit def proxyMonoid[T](p: Rep[Monoid[T]]): Monoid[T] = {
    if (p.rhs.isInstanceOf[Monoid[T]@unchecked]) p.rhs.asInstanceOf[Monoid[T]]
    else
      MonoidAdapter(p)
  }

  // familyElem
  class MonoidElem[T, To <: Monoid[T]](implicit _eT: Elem[T])
    extends EntityElem[To] {
    def eT = _eT

    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("T" -> (eT -> scalan.util.Invariant))
    override lazy val tag = {
      implicit val tagT = eT.tag
      weakTypeTag[Monoid[T]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[Monoid[T]] => convertMonoid(x) }
      tryConvert(element[Monoid[T]], this, x, conv)
    }

    def convertMonoid(x: Rep[Monoid[T]]): Rep[To] = {
      x.elem match {
        case _: MonoidElem[_, _] => asRep[To](x)
        case e => !!!(s"Expected $x to have MonoidElem[_, _], but got $e", x)
      }
    }
  }

  implicit def monoidElement[T](implicit eT: Elem[T]): Elem[Monoid[T]] =
    cachedElemByClass(eT)(classOf[MonoidElem[T, Monoid[T]]])

  implicit case object MonoidCompanionElem extends CompanionElem[MonoidCompanionCtor] {
    lazy val tag = weakTypeTag[MonoidCompanionCtor]
  }

  abstract class MonoidCompanionCtor extends CompanionDef[MonoidCompanionCtor] with MonoidCompanion {
    def selfType = MonoidCompanionElem
    override def toString = "Monoid"
  }
  implicit def proxyMonoidCompanionCtor(p: Rep[MonoidCompanionCtor]): MonoidCompanionCtor =
    proxyOps[MonoidCompanionCtor](p)

  lazy val RMonoid: Rep[MonoidCompanionCtor] = new MonoidCompanionCtor {
    private val thisClass = classOf[MonoidCompanion]
  }

  object MonoidMethods {
    object zero {
      def unapply(d: Def[_]): Nullable[Rep[Monoid[T]] forSome {type T}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidElem[_, _]] && method.getName == "zero" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[Monoid[T]] forSome {type T}]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[Monoid[T]] forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object plus {
      def unapply(d: Def[_]): Nullable[(Rep[Monoid[T]], Rep[T], Rep[T]) forSome {type T}] = d match {
        case MethodCall(receiver, method, args, _) if receiver.elem.isInstanceOf[MonoidElem[_, _]] && method.getName == "plus" =>
          val res = (receiver, args(0), args(1))
          Nullable(res).asInstanceOf[Nullable[(Rep[Monoid[T]], Rep[T], Rep[T]) forSome {type T}]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[(Rep[Monoid[T]], Rep[T], Rep[T]) forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object power {
      def unapply(d: Def[_]): Nullable[(Rep[Monoid[T]], Rep[T], Rep[Int]) forSome {type T}] = d match {
        case MethodCall(receiver, method, args, _) if receiver.elem.isInstanceOf[MonoidElem[_, _]] && method.getName == "power" =>
          val res = (receiver, args(0), args(1))
          Nullable(res).asInstanceOf[Nullable[(Rep[Monoid[T]], Rep[T], Rep[Int]) forSome {type T}]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[(Rep[Monoid[T]], Rep[T], Rep[Int]) forSome {type T}] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }
  }

  object MonoidCompanionMethods {
  }
} // of object Monoid
  registerEntityObject("Monoid", Monoid)

object MonoidBuilder extends EntityObject("MonoidBuilder") {
  // entityAdapter for MonoidBuilder trait
  case class MonoidBuilderAdapter(source: Rep[MonoidBuilder])
      extends MonoidBuilder with Def[MonoidBuilder] {
    val selfType: Elem[MonoidBuilder] = element[MonoidBuilder]
    override def transform(t: Transformer) = MonoidBuilderAdapter(t(source))
    private val thisClass = classOf[MonoidBuilder]

    def intPlusMonoid: Rep[Monoid[Int]] = {
      asRep[Monoid[Int]](mkMethodCall(source,
        thisClass.getMethod("intPlusMonoid"),
        List(),
        true, true, element[Monoid[Int]]))
    }

    def intMaxMonoid: Rep[Monoid[Int]] = {
      asRep[Monoid[Int]](mkMethodCall(source,
        thisClass.getMethod("intMaxMonoid"),
        List(),
        true, true, element[Monoid[Int]]))
    }

    def intMinMonoid: Rep[Monoid[Int]] = {
      asRep[Monoid[Int]](mkMethodCall(source,
        thisClass.getMethod("intMinMonoid"),
        List(),
        true, true, element[Monoid[Int]]))
    }

    def longPlusMonoid: Rep[Monoid[Long]] = {
      asRep[Monoid[Long]](mkMethodCall(source,
        thisClass.getMethod("longPlusMonoid"),
        List(),
        true, true, element[Monoid[Long]]))
    }

    def longMaxMonoid: Rep[Monoid[Long]] = {
      asRep[Monoid[Long]](mkMethodCall(source,
        thisClass.getMethod("longMaxMonoid"),
        List(),
        true, true, element[Monoid[Long]]))
    }

    def longMinMonoid: Rep[Monoid[Long]] = {
      asRep[Monoid[Long]](mkMethodCall(source,
        thisClass.getMethod("longMinMonoid"),
        List(),
        true, true, element[Monoid[Long]]))
    }

    def pairMonoid[A, B](m1: Rep[Monoid[A]], m2: Rep[Monoid[B]]): Rep[Monoid[(A, B)]] = {
      implicit val eA = m1.eT
implicit val eB = m2.eT
      asRep[Monoid[(A, B)]](mkMethodCall(source,
        thisClass.getMethod("pairMonoid", classOf[Sym], classOf[Sym]),
        List(m1, m2),
        true, true, element[Monoid[(A, B)]]))
    }
  }

  // entityProxy: single proxy for each type family
  implicit def proxyMonoidBuilder(p: Rep[MonoidBuilder]): MonoidBuilder = {
    if (p.rhs.isInstanceOf[MonoidBuilder@unchecked]) p.rhs.asInstanceOf[MonoidBuilder]
    else
      MonoidBuilderAdapter(p)
  }

  // familyElem
  class MonoidBuilderElem[To <: MonoidBuilder]
    extends EntityElem[To] {
    override lazy val tag = {
      weakTypeTag[MonoidBuilder].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[MonoidBuilder] => convertMonoidBuilder(x) }
      tryConvert(element[MonoidBuilder], this, x, conv)
    }

    def convertMonoidBuilder(x: Rep[MonoidBuilder]): Rep[To] = {
      x.elem match {
        case _: MonoidBuilderElem[_] => asRep[To](x)
        case e => !!!(s"Expected $x to have MonoidBuilderElem[_], but got $e", x)
      }
    }
  }

  implicit lazy val monoidBuilderElement: Elem[MonoidBuilder] =
    new MonoidBuilderElem[MonoidBuilder]

  implicit case object MonoidBuilderCompanionElem extends CompanionElem[MonoidBuilderCompanionCtor] {
    lazy val tag = weakTypeTag[MonoidBuilderCompanionCtor]
  }

  abstract class MonoidBuilderCompanionCtor extends CompanionDef[MonoidBuilderCompanionCtor] with MonoidBuilderCompanion {
    def selfType = MonoidBuilderCompanionElem
    override def toString = "MonoidBuilder"
  }
  implicit def proxyMonoidBuilderCompanionCtor(p: Rep[MonoidBuilderCompanionCtor]): MonoidBuilderCompanionCtor =
    proxyOps[MonoidBuilderCompanionCtor](p)

  lazy val RMonoidBuilder: Rep[MonoidBuilderCompanionCtor] = new MonoidBuilderCompanionCtor {
    private val thisClass = classOf[MonoidBuilderCompanion]
  }

  object MonoidBuilderMethods {
    object intPlusMonoid {
      def unapply(d: Def[_]): Nullable[Rep[MonoidBuilder]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "intPlusMonoid" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[MonoidBuilder]]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[MonoidBuilder]] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object intMaxMonoid {
      def unapply(d: Def[_]): Nullable[Rep[MonoidBuilder]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "intMaxMonoid" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[MonoidBuilder]]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[MonoidBuilder]] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object intMinMonoid {
      def unapply(d: Def[_]): Nullable[Rep[MonoidBuilder]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "intMinMonoid" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[MonoidBuilder]]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[MonoidBuilder]] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object longPlusMonoid {
      def unapply(d: Def[_]): Nullable[Rep[MonoidBuilder]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "longPlusMonoid" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[MonoidBuilder]]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[MonoidBuilder]] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object longMaxMonoid {
      def unapply(d: Def[_]): Nullable[Rep[MonoidBuilder]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "longMaxMonoid" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[MonoidBuilder]]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[MonoidBuilder]] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object longMinMonoid {
      def unapply(d: Def[_]): Nullable[Rep[MonoidBuilder]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "longMinMonoid" =>
          val res = receiver
          Nullable(res).asInstanceOf[Nullable[Rep[MonoidBuilder]]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[Rep[MonoidBuilder]] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }

    object pairMonoid {
      def unapply(d: Def[_]): Nullable[(Rep[MonoidBuilder], Rep[Monoid[A]], Rep[Monoid[B]]) forSome {type A; type B}] = d match {
        case MethodCall(receiver, method, args, _) if receiver.elem.isInstanceOf[MonoidBuilderElem[_]] && method.getName == "pairMonoid" =>
          val res = (receiver, args(0), args(1))
          Nullable(res).asInstanceOf[Nullable[(Rep[MonoidBuilder], Rep[Monoid[A]], Rep[Monoid[B]]) forSome {type A; type B}]]
        case _ => Nullable.None
      }
      def unapply(exp: Sym): Nullable[(Rep[MonoidBuilder], Rep[Monoid[A]], Rep[Monoid[B]]) forSome {type A; type B}] = exp match {
        case Def(d) => unapply(d)
        case _ => Nullable.None
      }
    }
  }

  object MonoidBuilderCompanionMethods {
  }
} // of object MonoidBuilder
  registerEntityObject("MonoidBuilder", MonoidBuilder)

  registerModule(MonoidsModule)
}

object MonoidsModule extends scalan.ModuleInfo("special.collection", "Monoids")
}

trait MonoidsModule extends special.collection.impl.MonoidsDefs {self: Library =>}

package scala.util

import scalan._
import impl._
import scala.wrappers.WrappersModule
import scala.reflect.runtime.universe._
import scala.reflect._

package impl {
// Abs -----------------------------------
trait WEithersDefs extends scalan.Scalan with WEithers {
  self: WrappersModule =>

  // entityProxy: single proxy for each type family
  implicit def proxyWEither[A, B](p: Rep[WEither[A, B]]): WEither[A, B] = {
    proxyOps[WEither[A, B]](p)(scala.reflect.classTag[WEither[A, B]])
  }

  // familyElem
  class WEitherElem[A, B, To <: WEither[A, B]](implicit _eA: Elem[A], _eB: Elem[B])
    extends EntityElem[To] {
    def eA = _eA
    def eB = _eB
    lazy val parent: Option[Elem[_]] = None
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("A" -> (eA -> scalan.util.Invariant), "B" -> (eB -> scalan.util.Invariant))
    override lazy val tag = {
      implicit val tagA = eA.tag
      implicit val tagB = eB.tag
      weakTypeTag[WEither[A, B]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      val conv = fun {x: Rep[WEither[A, B]] => convertWEither(x) }
      tryConvert(element[WEither[A, B]], this, x, conv)
    }

    def convertWEither(x: Rep[WEither[A, B]]): Rep[To] = {
      x.elem match {
        case _: WEitherElem[_, _, _] => x.asRep[To]
        case e => !!!(s"Expected $x to have WEitherElem[_, _, _], but got $e", x)
      }
    }
    override def getDefaultRep: Rep[To] = ???
  }

  implicit def wEitherElement[A, B](implicit eA: Elem[A], eB: Elem[B]): Elem[WEither[A, B]] =
    cachedElem[WEitherElem[A, B, WEither[A, B]]](eA, eB)

  implicit case object WEitherCompanionElem extends CompanionElem[WEitherCompanionCtor] {
    lazy val tag = weakTypeTag[WEitherCompanionCtor]
    protected def getDefaultRep = WEither
  }

  abstract class WEitherCompanionCtor extends CompanionDef[WEitherCompanionCtor] with WEitherCompanion {
    def selfType = WEitherCompanionElem
    override def toString = "WEither"
  }
  implicit def proxyWEitherCompanionCtor(p: Rep[WEitherCompanionCtor]): WEitherCompanionCtor =
    proxyOps[WEitherCompanionCtor](p)

  lazy val WEither: Rep[WEitherCompanionCtor] = new WEitherCompanionCtor {
    def cond[A, B](test: Rep[Boolean], right: Rep[Thunk[B]], left: Rep[Thunk[A]]): Rep[WEither[A, B]] = {
      implicit val eA = left.elem.eItem
implicit val eB = right.elem.eItem
      mkMethodCall(self,
        this.getClass.getMethod("cond", classOf[Sym], classOf[Sym], classOf[Sym]),
        List(test, right, left),
        true, element[WEither[A, B]]).asRep[WEither[A, B]]
    }
  }

  object WEitherMethods {
    object fold {
      def unapply(d: Def[_]): Option[(Rep[WEither[A, B]], Rep[A => C], Rep[B => C]) forSome {type A; type B; type C}] = d match {
        case MethodCall(receiver, method, Seq(fa, fb, _*), _) if receiver.elem.isInstanceOf[WEitherElem[_, _, _]] && method.getName == "fold" =>
          Some((receiver, fa, fb)).asInstanceOf[Option[(Rep[WEither[A, B]], Rep[A => C], Rep[B => C]) forSome {type A; type B; type C}]]
        case _ => None
      }
      def unapply(exp: Sym): Option[(Rep[WEither[A, B]], Rep[A => C], Rep[B => C]) forSome {type A; type B; type C}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object WEitherCompanionMethods {
    object cond {
      def unapply(d: Def[_]): Option[(Rep[Boolean], Rep[Thunk[B]], Rep[Thunk[A]]) forSome {type A; type B}] = d match {
        case MethodCall(receiver, method, Seq(test, right, left, _*), _) if receiver.elem == WEitherCompanionElem && method.getName == "cond" =>
          Some((test, right, left)).asInstanceOf[Option[(Rep[Boolean], Rep[Thunk[B]], Rep[Thunk[A]]) forSome {type A; type B}]]
        case _ => None
      }
      def unapply(exp: Sym): Option[(Rep[Boolean], Rep[Thunk[B]], Rep[Thunk[A]]) forSome {type A; type B}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  registerModule(WEithersModule)
}

object WEithersModule extends scalan.ModuleInfo("scala.util", "WEithers")
}

trait WEithersModule extends scala.util.impl.WEithersDefs {self: WrappersModule =>}
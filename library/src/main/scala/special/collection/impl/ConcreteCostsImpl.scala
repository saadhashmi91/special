package special.collection

import scalan._
import scala.reflect.runtime.universe._
import scala.reflect._
import scala.collection.mutable.WrappedArray

package impl {
import scalan.util.MemoizedFunc // manual fix

// Abs -----------------------------------
trait ConcreteCostsDefs extends scalan.Scalan with ConcreteCosts {
  self: Library =>
import IsoUR._
import Converter._
import CCostedBuilder._
import CCostedColl._
import CCostedFunc._
import CCostedOption._
import CCostedPair._
import CCostedPrim._
import CSizeColl._
import CSizeFunc._
import CSizeOption._
import CSizePair._
import CSizePrim._
import Coll._
import Costed._
import CostedBuilder._
import CostedColl._
import CostedFunc._
import CostedOption._
import CostedPair._
import CostedPrim._
import MonoidBuilder._
import Size._
import SizeColl._
import SizeFunc._
import SizeOption._
import SizePair._
import SizePrim._
import WOption._
import WRType._
import WSpecialPredef._

object CCostedPrim extends EntityObject("CCostedPrim") {
  case class CCostedPrimCtor[Val]
      (override val value: Ref[Val], override val cost: Ref[Int], override val size: Ref[Size[Val]])
    extends CCostedPrim[Val](value, cost, size) with Def[CCostedPrim[Val]] {
    implicit lazy val eVal = value.elem

    lazy val resultType = element[CCostedPrim[Val]]
    override def transform(t: Transformer) = CCostedPrimCtor[Val](t(value), t(cost), t(size))
  }
  // elem for concrete class
  class CCostedPrimElem[Val](val iso: Iso[CCostedPrimData[Val], CCostedPrim[Val]])(implicit override val eVal: Elem[Val])
    extends CostedPrimElem[Val, CCostedPrim[Val]]
    with ConcreteElem[CCostedPrimData[Val], CCostedPrim[Val]] {
    override lazy val parent: Option[Elem[_]] = Some(costedPrimElement(element[Val]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("Val" -> (eVal -> scalan.util.Invariant))
  }

  // state representation type
  type CCostedPrimData[Val] = (Val, (Int, Size[Val]))

  // 3) Iso for concrete class
  class CCostedPrimIso[Val](implicit eVal: Elem[Val])
    extends EntityIso[CCostedPrimData[Val], CCostedPrim[Val]] with Def[CCostedPrimIso[Val]] {
    override def transform(t: Transformer) = new CCostedPrimIso[Val]()(eVal)
    private lazy val _safeFrom = fun { p: Ref[CCostedPrim[Val]] => (p.value, p.cost, p.size) }
    override def from(p: Ref[CCostedPrim[Val]]) =
      tryConvert[CCostedPrim[Val], (Val, (Int, Size[Val]))](eTo, eFrom, p, _safeFrom)
    override def to(p: Ref[(Val, (Int, Size[Val]))]) = {
      val Pair(value, Pair(cost, size)) = p
      RCCostedPrim(value, cost, size)
    }
    lazy val eFrom = pairElement(element[Val], pairElement(element[Int], element[Size[Val]]))
    lazy val eTo = new CCostedPrimElem[Val](self)
    lazy val resultType = new CCostedPrimIsoElem[Val](eVal)
    def productArity = 1
    def productElement(n: Int) = eVal
  }
  case class CCostedPrimIsoElem[Val](eVal: Elem[Val]) extends Elem[CCostedPrimIso[Val]] {
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("Val" -> (eVal -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class CCostedPrimCompanionCtor extends CompanionDef[CCostedPrimCompanionCtor] with CCostedPrimCompanion {
    def resultType = CCostedPrimCompanionElem
    override def toString = "CCostedPrimCompanion"
    @scalan.OverloadId("fromData")
    def apply[Val](p: Ref[CCostedPrimData[Val]]): Ref[CCostedPrim[Val]] = {
      implicit val eVal = p._1.elem
      isoCCostedPrim[Val].to(p)
    }

    // manual fix
    @scalan.OverloadId("fromFields")
    def apply[Val](value: Ref[Val], cost: Ref[Int], size: Ref[Size[Val]]): Ref[CCostedPrim[Val]] = {
      assertValueIdForOpCost(value, cost)
      mkCCostedPrim(value, cost, size)
    }

    def unapply[Val](p: Ref[CostedPrim[Val]]) = unmkCCostedPrim(p)
  }
  lazy val CCostedPrimRef: Ref[CCostedPrimCompanionCtor] = new CCostedPrimCompanionCtor
  lazy val RCCostedPrim: CCostedPrimCompanionCtor = unrefCCostedPrimCompanion(CCostedPrimRef)
  implicit def unrefCCostedPrimCompanion(p: Ref[CCostedPrimCompanionCtor]): CCostedPrimCompanionCtor = {
    if (p.rhs.isInstanceOf[CCostedPrimCompanionCtor])
      p.rhs.asInstanceOf[CCostedPrimCompanionCtor]
    else
      unrefDelegate[CCostedPrimCompanionCtor](p)
  }

  implicit case object CCostedPrimCompanionElem extends CompanionElem[CCostedPrimCompanionCtor]

  implicit def unrefCCostedPrim[Val](p: Ref[CCostedPrim[Val]]): CCostedPrim[Val] = {
    if (p.rhs.isInstanceOf[CCostedPrim[Val]@unchecked])
      p.rhs.asInstanceOf[CCostedPrim[Val]]
    else
      unrefDelegate[CCostedPrim[Val]](p)
  }

  implicit class ExtendedCCostedPrim[Val](p: Ref[CCostedPrim[Val]]) {
    def toData: Ref[CCostedPrimData[Val]] = {
      implicit val eVal = p.value.elem
      isoCCostedPrim(eVal).from(p)
    }
  }

  // 5) implicit resolution of Iso
  // manual fix
  private[ConcreteCostsDefs] val _isoCCostedPrimMemo = new MemoizedFunc({ case eVal: Elem[v] =>
    reifyObject(new CCostedPrimIso[v]()(eVal))
  })
  implicit def isoCCostedPrim[Val](implicit eVal: Elem[Val]): Iso[CCostedPrimData[Val], CCostedPrim[Val]] =
    _isoCCostedPrimMemo(eVal).asInstanceOf[Iso[CCostedPrimData[Val], CCostedPrim[Val]]]

  def mkCCostedPrim[Val]
    (value: Ref[Val], cost: Ref[Int], size: Ref[Size[Val]]): Ref[CCostedPrim[Val]] = {
    new CCostedPrimCtor[Val](value, cost, size)
  }
  def unmkCCostedPrim[Val](p: Ref[CostedPrim[Val]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: CCostedPrimElem[Val] @unchecked =>
      Some((asRep[CCostedPrim[Val]](p).value, asRep[CCostedPrim[Val]](p).cost, asRep[CCostedPrim[Val]](p).size))
    case _ =>
      None
  }
} // of object CCostedPrim
  registerEntityObject("CCostedPrim", CCostedPrim)

object CCostedPair extends EntityObject("CCostedPair") {
  case class CCostedPairCtor[L, R]
      (override val l: Ref[Costed[L]], override val r: Ref[Costed[R]], override val accCost: Ref[Int])
    extends CCostedPair[L, R](l, r, accCost) with Def[CCostedPair[L, R]] {
    implicit lazy val eL = l.eVal;
implicit lazy val eR = r.eVal
    override lazy val eVal: Elem[(L, R)] = implicitly[Elem[(L, R)]]
    lazy val resultType = element[CCostedPair[L, R]]
    override def transform(t: Transformer) = CCostedPairCtor[L, R](t(l), t(r), t(accCost))
    private val thisClass = classOf[CostedPair[_, _]]

    override def cost: Ref[Int] = {
      asRep[Int](mkMethodCall(self,
        thisClass.getMethod("cost"),
        WrappedArray.empty,
        true, false, element[Int]))
    }
  }
  // elem for concrete class
  class CCostedPairElem[L, R](val iso: Iso[CCostedPairData[L, R], CCostedPair[L, R]])(implicit override val eL: Elem[L], override val eR: Elem[R])
    extends CostedPairElem[L, R, CCostedPair[L, R]]
    with ConcreteElem[CCostedPairData[L, R], CCostedPair[L, R]] {
    override lazy val parent: Option[Elem[_]] = Some(costedPairElement(element[L], element[R]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("L" -> (eL -> scalan.util.Invariant), "R" -> (eR -> scalan.util.Invariant))
  }

  // state representation type
  type CCostedPairData[L, R] = (Costed[L], (Costed[R], Int))

  // 3) Iso for concrete class
  class CCostedPairIso[L, R](implicit eL: Elem[L], eR: Elem[R])
    extends EntityIso[CCostedPairData[L, R], CCostedPair[L, R]] with Def[CCostedPairIso[L, R]] {
    override def transform(t: Transformer) = new CCostedPairIso[L, R]()(eL, eR)
    private lazy val _safeFrom = fun { p: Ref[CCostedPair[L, R]] => (p.l, p.r, p.accCost) }
    override def from(p: Ref[CCostedPair[L, R]]) =
      tryConvert[CCostedPair[L, R], (Costed[L], (Costed[R], Int))](eTo, eFrom, p, _safeFrom)
    override def to(p: Ref[(Costed[L], (Costed[R], Int))]) = {
      val Pair(l, Pair(r, accCost)) = p
      RCCostedPair(l, r, accCost)
    }
    lazy val eFrom = pairElement(element[Costed[L]], pairElement(element[Costed[R]], element[Int]))
    lazy val eTo = new CCostedPairElem[L, R](self)
    lazy val resultType = new CCostedPairIsoElem[L, R](eL, eR)
    def productArity = 2
    def productElement(n: Int) = n match {
      case 0 => eL
      case 1 => eR
    }
  }
  case class CCostedPairIsoElem[L, R](eL: Elem[L], eR: Elem[R]) extends Elem[CCostedPairIso[L, R]] {
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("L" -> (eL -> scalan.util.Invariant), "R" -> (eR -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class CCostedPairCompanionCtor extends CompanionDef[CCostedPairCompanionCtor] with CCostedPairCompanion {
    def resultType = CCostedPairCompanionElem
    override def toString = "CCostedPairCompanion"
    @scalan.OverloadId("fromData")
    def apply[L, R](p: Ref[CCostedPairData[L, R]]): Ref[CCostedPair[L, R]] = {
      implicit val eL = p._1.eVal;
implicit val eR = p._2.eVal
      isoCCostedPair[L, R].to(p)
    }

    // manual fix
    @scalan.OverloadId("fromFields")
    def apply[L, R](l: Ref[Costed[L]], r: Ref[Costed[R]], accCost: Ref[Int]): Ref[CCostedPair[L, R]] = {
      assertValueIdForOpCost(Pair(l, r), accCost)
      mkCCostedPair(l, r, accCost)
    }

    def unapply[L, R](p: Ref[CostedPair[L, R]]) = unmkCCostedPair(p)
  }
  lazy val CCostedPairRef: Ref[CCostedPairCompanionCtor] = new CCostedPairCompanionCtor
  lazy val RCCostedPair: CCostedPairCompanionCtor = unrefCCostedPairCompanion(CCostedPairRef)
  implicit def unrefCCostedPairCompanion(p: Ref[CCostedPairCompanionCtor]): CCostedPairCompanionCtor = {
    if (p.rhs.isInstanceOf[CCostedPairCompanionCtor])
      p.rhs.asInstanceOf[CCostedPairCompanionCtor]
    else
      unrefDelegate[CCostedPairCompanionCtor](p)
  }

  implicit case object CCostedPairCompanionElem extends CompanionElem[CCostedPairCompanionCtor]

  implicit def unrefCCostedPair[L, R](p: Ref[CCostedPair[L, R]]): CCostedPair[L, R] = {
    if (p.rhs.isInstanceOf[CCostedPair[L, R]@unchecked])
      p.rhs.asInstanceOf[CCostedPair[L, R]]
    else
      unrefDelegate[CCostedPair[L, R]](p)
  }

  implicit class ExtendedCCostedPair[L, R](p: Ref[CCostedPair[L, R]]) {
    def toData: Ref[CCostedPairData[L, R]] = {
      implicit val eL = p.l.eVal;
implicit val eR = p.r.eVal
      isoCCostedPair(eL, eR).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoCCostedPair[L, R](implicit eL: Elem[L], eR: Elem[R]): Iso[CCostedPairData[L, R], CCostedPair[L, R]] =
    reifyObject(new CCostedPairIso[L, R]()(eL, eR))

  def mkCCostedPair[L, R]
    (l: Ref[Costed[L]], r: Ref[Costed[R]], accCost: Ref[Int]): Ref[CCostedPair[L, R]] = {
    new CCostedPairCtor[L, R](l, r, accCost)
  }
  def unmkCCostedPair[L, R](p: Ref[CostedPair[L, R]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: CCostedPairElem[L, R] @unchecked =>
      Some((asRep[CCostedPair[L, R]](p).l, asRep[CCostedPair[L, R]](p).r, asRep[CCostedPair[L, R]](p).accCost))
    case _ =>
      None
  }
} // of object CCostedPair
  registerEntityObject("CCostedPair", CCostedPair)

object CCostedFunc extends EntityObject("CCostedFunc") {
  case class CCostedFuncCtor[Env, Arg, Res]
      (override val envCosted: Ref[Costed[Env]], override val func: Ref[Costed[Arg] => Costed[Res]], override val cost: Ref[Int], override val size: Ref[Size[Arg => Res]])
    extends CCostedFunc[Env, Arg, Res](envCosted, func, cost, size) with Def[CCostedFunc[Env, Arg, Res]] {
    implicit lazy val eEnv = envCosted.eVal;
implicit lazy val eArg = func.elem.eDom.typeArgs("Val")._1.asElem[Arg];
implicit lazy val eRes = func.elem.eRange.typeArgs("Val")._1.asElem[Res]
    override lazy val eVal: Elem[Arg => Res] = implicitly[Elem[Arg => Res]]
    lazy val resultType = element[CCostedFunc[Env, Arg, Res]]
    override def transform(t: Transformer) = CCostedFuncCtor[Env, Arg, Res](t(envCosted), t(func), t(cost), t(size))
    private val thisClass = classOf[CostedFunc[_, _, _]]

    override def value: Ref[Arg => Res] = {
      asRep[Arg => Res](mkMethodCall(self,
        thisClass.getMethod("value"),
        WrappedArray.empty,
        true, false, element[Arg => Res]))
    }
  }
  // elem for concrete class
  class CCostedFuncElem[Env, Arg, Res](val iso: Iso[CCostedFuncData[Env, Arg, Res], CCostedFunc[Env, Arg, Res]])(implicit override val eEnv: Elem[Env], override val eArg: Elem[Arg], override val eRes: Elem[Res])
    extends CostedFuncElem[Env, Arg, Res, CCostedFunc[Env, Arg, Res]]
    with ConcreteElem[CCostedFuncData[Env, Arg, Res], CCostedFunc[Env, Arg, Res]] {
    override lazy val parent: Option[Elem[_]] = Some(costedFuncElement(element[Env], element[Arg], element[Res]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("Env" -> (eEnv -> scalan.util.Invariant), "Arg" -> (eArg -> scalan.util.Invariant), "Res" -> (eRes -> scalan.util.Invariant))
  }

  // state representation type
  type CCostedFuncData[Env, Arg, Res] = (Costed[Env], (Costed[Arg] => Costed[Res], (Int, Size[Arg => Res])))

  // 3) Iso for concrete class
  class CCostedFuncIso[Env, Arg, Res](implicit eEnv: Elem[Env], eArg: Elem[Arg], eRes: Elem[Res])
    extends EntityIso[CCostedFuncData[Env, Arg, Res], CCostedFunc[Env, Arg, Res]] with Def[CCostedFuncIso[Env, Arg, Res]] {
    override def transform(t: Transformer) = new CCostedFuncIso[Env, Arg, Res]()(eEnv, eArg, eRes)
    private lazy val _safeFrom = fun { p: Ref[CCostedFunc[Env, Arg, Res]] => (p.envCosted, p.func, p.cost, p.size) }
    override def from(p: Ref[CCostedFunc[Env, Arg, Res]]) =
      tryConvert[CCostedFunc[Env, Arg, Res], (Costed[Env], (Costed[Arg] => Costed[Res], (Int, Size[Arg => Res])))](eTo, eFrom, p, _safeFrom)
    override def to(p: Ref[(Costed[Env], (Costed[Arg] => Costed[Res], (Int, Size[Arg => Res])))]) = {
      val Pair(envCosted, Pair(func, Pair(cost, size))) = p
      RCCostedFunc(envCosted, func, cost, size)
    }
    lazy val eFrom = pairElement(element[Costed[Env]], pairElement(element[Costed[Arg] => Costed[Res]], pairElement(element[Int], element[Size[Arg => Res]])))
    lazy val eTo = new CCostedFuncElem[Env, Arg, Res](self)
    lazy val resultType = new CCostedFuncIsoElem[Env, Arg, Res](eEnv, eArg, eRes)
    def productArity = 3
    def productElement(n: Int) = n match {
      case 0 => eEnv
      case 1 => eArg
      case 2 => eRes
    }
  }
  case class CCostedFuncIsoElem[Env, Arg, Res](eEnv: Elem[Env], eArg: Elem[Arg], eRes: Elem[Res]) extends Elem[CCostedFuncIso[Env, Arg, Res]] {
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("Env" -> (eEnv -> scalan.util.Invariant), "Arg" -> (eArg -> scalan.util.Invariant), "Res" -> (eRes -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class CCostedFuncCompanionCtor extends CompanionDef[CCostedFuncCompanionCtor] with CCostedFuncCompanion {
    def resultType = CCostedFuncCompanionElem
    override def toString = "CCostedFuncCompanion"
    @scalan.OverloadId("fromData")
    def apply[Env, Arg, Res](p: Ref[CCostedFuncData[Env, Arg, Res]]): Ref[CCostedFunc[Env, Arg, Res]] = {
      implicit val eEnv = p._1.eVal;
implicit val eArg = p._2.elem.eDom.typeArgs("Val")._1.asElem[Arg];
implicit val eRes = p._2.elem.eRange.typeArgs("Val")._1.asElem[Res]
      isoCCostedFunc[Env, Arg, Res].to(p)
    }

    @scalan.OverloadId("fromFields")
    def apply[Env, Arg, Res](envCosted: Ref[Costed[Env]], func: Ref[Costed[Arg] => Costed[Res]], cost: Ref[Int], size: Ref[Size[Arg => Res]]): Ref[CCostedFunc[Env, Arg, Res]] =
      mkCCostedFunc(envCosted, func, cost, size)

    def unapply[Env, Arg, Res](p: Ref[CostedFunc[Env, Arg, Res]]) = unmkCCostedFunc(p)
  }
  lazy val CCostedFuncRef: Ref[CCostedFuncCompanionCtor] = new CCostedFuncCompanionCtor
  lazy val RCCostedFunc: CCostedFuncCompanionCtor = unrefCCostedFuncCompanion(CCostedFuncRef)
  implicit def unrefCCostedFuncCompanion(p: Ref[CCostedFuncCompanionCtor]): CCostedFuncCompanionCtor = {
    if (p.rhs.isInstanceOf[CCostedFuncCompanionCtor])
      p.rhs.asInstanceOf[CCostedFuncCompanionCtor]
    else
      unrefDelegate[CCostedFuncCompanionCtor](p)
  }

  implicit case object CCostedFuncCompanionElem extends CompanionElem[CCostedFuncCompanionCtor]

  implicit def unrefCCostedFunc[Env, Arg, Res](p: Ref[CCostedFunc[Env, Arg, Res]]): CCostedFunc[Env, Arg, Res] = {
    if (p.rhs.isInstanceOf[CCostedFunc[Env, Arg, Res]@unchecked])
      p.rhs.asInstanceOf[CCostedFunc[Env, Arg, Res]]
    else
      unrefDelegate[CCostedFunc[Env, Arg, Res]](p)
  }

  implicit class ExtendedCCostedFunc[Env, Arg, Res](p: Ref[CCostedFunc[Env, Arg, Res]]) {
    def toData: Ref[CCostedFuncData[Env, Arg, Res]] = {
      implicit val eEnv = p.envCosted.eVal;
implicit val eArg = p.func.elem.eDom.typeArgs("Val")._1.asElem[Arg];
implicit val eRes = p.func.elem.eRange.typeArgs("Val")._1.asElem[Res]
      isoCCostedFunc(eEnv, eArg, eRes).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoCCostedFunc[Env, Arg, Res](implicit eEnv: Elem[Env], eArg: Elem[Arg], eRes: Elem[Res]): Iso[CCostedFuncData[Env, Arg, Res], CCostedFunc[Env, Arg, Res]] =
    reifyObject(new CCostedFuncIso[Env, Arg, Res]()(eEnv, eArg, eRes))

  def mkCCostedFunc[Env, Arg, Res]
    (envCosted: Ref[Costed[Env]], func: Ref[Costed[Arg] => Costed[Res]], cost: Ref[Int], size: Ref[Size[Arg => Res]]): Ref[CCostedFunc[Env, Arg, Res]] = {
    new CCostedFuncCtor[Env, Arg, Res](envCosted, func, cost, size)
  }
  def unmkCCostedFunc[Env, Arg, Res](p: Ref[CostedFunc[Env, Arg, Res]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: CCostedFuncElem[Env, Arg, Res] @unchecked =>
      Some((asRep[CCostedFunc[Env, Arg, Res]](p).envCosted, asRep[CCostedFunc[Env, Arg, Res]](p).func, asRep[CCostedFunc[Env, Arg, Res]](p).cost, asRep[CCostedFunc[Env, Arg, Res]](p).size))
    case _ =>
      None
  }
} // of object CCostedFunc
  registerEntityObject("CCostedFunc", CCostedFunc)

object CCostedColl extends EntityObject("CCostedColl") {
  case class CCostedCollCtor[Item]
      (override val values: Ref[Coll[Item]], override val costs: Ref[Coll[Int]], override val sizes: Ref[Coll[Size[Item]]], override val valuesCost: Ref[Int])
    extends CCostedColl[Item](values, costs, sizes, valuesCost) with Def[CCostedColl[Item]] {
    implicit lazy val eItem = values.eA
    override lazy val eVal: Elem[Coll[Item]] = implicitly[Elem[Coll[Item]]]
    lazy val resultType = element[CCostedColl[Item]]
    override def transform(t: Transformer) = CCostedCollCtor[Item](t(values), t(costs), t(sizes), t(valuesCost))
    private val thisClass = classOf[CostedColl[_]]

    override def cost: Ref[Int] = {
      asRep[Int](mkMethodCall(self,
        thisClass.getMethod("cost"),
        WrappedArray.empty,
        true, false, element[Int]))
    }

    override def mapCosted[Res](f: Ref[Costed[Item] => Costed[Res]]): Ref[CostedColl[Res]] = {
      implicit val eRes = f.elem.eRange.typeArgs("Val")._1.asElem[Res]
      asRep[CostedColl[Res]](mkMethodCall(self,
        thisClass.getMethod("mapCosted", classOf[Sym]),
        Array[AnyRef](f),
        true, false, element[CostedColl[Res]]))
    }

    override def filterCosted(f: Ref[Costed[Item] => Costed[Boolean]]): Ref[CostedColl[Item]] = {
      asRep[CostedColl[Item]](mkMethodCall(self,
        thisClass.getMethod("filterCosted", classOf[Sym]),
        Array[AnyRef](f),
        true, false, element[CostedColl[Item]]))
    }

    override def foldCosted[B](zero: Ref[Costed[B]], op: Ref[Costed[(B, Item)] => Costed[B]]): Ref[Costed[B]] = {
      implicit val eB = zero.eVal
      asRep[Costed[B]](mkMethodCall(self,
        thisClass.getMethod("foldCosted", classOf[Sym], classOf[Sym]),
        Array[AnyRef](zero, op),
        true, false, element[Costed[B]]))
    }
  }
  // elem for concrete class
  class CCostedCollElem[Item](val iso: Iso[CCostedCollData[Item], CCostedColl[Item]])(implicit override val eItem: Elem[Item])
    extends CostedCollElem[Item, CCostedColl[Item]]
    with ConcreteElem[CCostedCollData[Item], CCostedColl[Item]] {
    override lazy val parent: Option[Elem[_]] = Some(costedCollElement(element[Item]))
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("Item" -> (eItem -> scalan.util.Invariant))
  }

  // state representation type
  type CCostedCollData[Item] = (Coll[Item], (Coll[Int], (Coll[Size[Item]], Int)))

  // 3) Iso for concrete class
  class CCostedCollIso[Item](implicit eItem: Elem[Item])
    extends EntityIso[CCostedCollData[Item], CCostedColl[Item]] with Def[CCostedCollIso[Item]] {
    override def transform(t: Transformer) = new CCostedCollIso[Item]()(eItem)
    private lazy val _safeFrom = fun { p: Ref[CCostedColl[Item]] => (p.values, p.costs, p.sizes, p.valuesCost) }
    override def from(p: Ref[CCostedColl[Item]]) =
      tryConvert[CCostedColl[Item], (Coll[Item], (Coll[Int], (Coll[Size[Item]], Int)))](eTo, eFrom, p, _safeFrom)
    override def to(p: Ref[(Coll[Item], (Coll[Int], (Coll[Size[Item]], Int)))]) = {
      val Pair(values, Pair(costs, Pair(sizes, valuesCost))) = p
      RCCostedColl(values, costs, sizes, valuesCost)
    }
    lazy val eFrom = pairElement(element[Coll[Item]], pairElement(element[Coll[Int]], pairElement(element[Coll[Size[Item]]], element[Int])))
    lazy val eTo = new CCostedCollElem[Item](self)
    lazy val resultType = new CCostedCollIsoElem[Item](eItem)
    def productArity = 1
    def productElement(n: Int) = eItem
  }
  case class CCostedCollIsoElem[Item](eItem: Elem[Item]) extends Elem[CCostedCollIso[Item]] {
    override def buildTypeArgs = super.buildTypeArgs ++ TypeArgs("Item" -> (eItem -> scalan.util.Invariant))
  }
  // 4) constructor and deconstructor
  class CCostedCollCompanionCtor extends CompanionDef[CCostedCollCompanionCtor] with CCostedCollCompanion {
    def resultType = CCostedCollCompanionElem
    override def toString = "CCostedCollCompanion"
    @scalan.OverloadId("fromData")
    def apply[Item](p: Ref[CCostedCollData[Item]]): Ref[CCostedColl[Item]] = {
      implicit val eItem = p._1.eA
      isoCCostedColl[Item].to(p)
    }

    // manual fix
    @scalan.OverloadId("fromFields")
    def apply[Item](values: Ref[Coll[Item]], costs: Ref[Coll[Int]], sizes: Ref[Coll[Size[Item]]], valuesCost: Ref[Int]): Ref[CCostedColl[Item]] = {
      assertValueIdForOpCost(values, valuesCost)
      mkCCostedColl(values, costs, sizes, valuesCost)
    }

    def unapply[Item](p: Ref[CostedColl[Item]]) = unmkCCostedColl(p)
  }
  lazy val CCostedCollRef: Ref[CCostedCollCompanionCtor] = new CCostedCollCompanionCtor
  lazy val RCCostedColl: CCostedCollCompanionCtor = unrefCCostedCollCompanion(CCostedCollRef)
  implicit def unrefCCostedCollCompanion(p: Ref[CCostedCollCompanionCtor]): CCostedCollCompanionCtor = {
    if (p.rhs.isInstanceOf[CCostedCollCompanionCtor])
      p.rhs.asInstanceOf[CCostedCollCompanionCtor]
    else
      unrefDelegate[CCostedCollCompanionCtor](p)
  }

  implicit case object CCostedCollCompanionElem extends CompanionElem[CCostedCollCompanionCtor]

  implicit def unrefCCostedColl[Item](p: Ref[CCostedColl[Item]]): CCostedColl[Item] = {
    if (p.rhs.isInstanceOf[CCostedColl[Item]@unchecked])
      p.rhs.asInstanceOf[CCostedColl[Item]]
    else
      unrefDelegate[CCostedColl[Item]](p)
  }

  implicit class ExtendedCCostedColl[Item](p: Ref[CCostedColl[Item]]) {
    def toData: Ref[CCostedCollData[Item]] = {
      implicit val eItem = p.values.eA
      isoCCostedColl(eItem).from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoCCostedColl[Item](implicit eItem: Elem[Item]): Iso[CCostedCollData[Item], CCostedColl[Item]] =
    reifyObject(new CCostedCollIso[Item]()(eItem))

  def mkCCostedColl[Item]
    (values: Ref[Coll[Item]], costs: Ref[Coll[Int]], sizes: Ref[Coll[Size[Item]]], valuesCost: Ref[Int]): Ref[CCostedColl[Item]] = {
    new CCostedCollCtor[Item](values, costs, sizes, valuesCost)
  }
  def unmkCCostedColl[Item](p: Ref[CostedColl[Item]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: CCostedCollElem[Item] @unchecked =>
      Some((asRep[CCostedColl[Item]](p).values, asRep[CCostedColl[Item]](p).costs, asRep[CCostedColl[Item]](p).sizes, asRep[CCostedColl[Item]](p).valuesCost))
    case _ =>
      None
  }
} // of object CCostedColl
  registerEntityObject("CCostedColl", CCostedColl)

object CCostedBuilder extends EntityObject("CCostedBuilder") {
  case class CCostedBuilderCtor
      ()
    extends CCostedBuilder() with Def[CCostedBuilder] {
    lazy val resultType = element[CCostedBuilder]
    override def transform(t: Transformer) = CCostedBuilderCtor()
    private val thisClass = classOf[CostedBuilder]

    override def monoidBuilder: Ref[MonoidBuilder] = {
      asRep[MonoidBuilder](mkMethodCall(self,
        thisClass.getMethod("monoidBuilder"),
        WrappedArray.empty,
        true, false, element[MonoidBuilder]))
    }

    override def costedValue[T](x: Ref[T], optCost: Ref[WOption[Int]]): Ref[Costed[T]] = {
      implicit val eT = x.elem
      asRep[Costed[T]](mkMethodCall(self,
        thisClass.getMethod("costedValue", classOf[Sym], classOf[Sym]),
        Array[AnyRef](x, optCost),
        true, false, element[Costed[T]]))
    }

    override def defaultValue[T](valueType: Ref[WRType[T]]): Ref[T] = {
      implicit val eT = valueType.eA
      asRep[T](mkMethodCall(self,
        thisClass.getMethod("defaultValue", classOf[Sym]),
        Array[AnyRef](valueType),
        true, false, element[T]))
    }
  }
  // elem for concrete class
  class CCostedBuilderElem(val iso: Iso[CCostedBuilderData, CCostedBuilder])
    extends CostedBuilderElem[CCostedBuilder]
    with ConcreteElem[CCostedBuilderData, CCostedBuilder] {
    override lazy val parent: Option[Elem[_]] = Some(costedBuilderElement)
  }

  // state representation type
  type CCostedBuilderData = Unit

  // 3) Iso for concrete class
  class CCostedBuilderIso
    extends EntityIso[CCostedBuilderData, CCostedBuilder] with Def[CCostedBuilderIso] {
    override def transform(t: Transformer) = new CCostedBuilderIso()
    private lazy val _safeFrom = fun { p: Ref[CCostedBuilder] => () }
    override def from(p: Ref[CCostedBuilder]) =
      tryConvert[CCostedBuilder, Unit](eTo, eFrom, p, _safeFrom)
    override def to(p: Ref[Unit]) = {
      val unit = p
      RCCostedBuilder()
    }
    lazy val eFrom = UnitElement
    lazy val eTo = new CCostedBuilderElem(self)
    lazy val resultType = new CCostedBuilderIsoElem
    def productArity = 0
    def productElement(n: Int) = ???
  }
  case class CCostedBuilderIsoElem() extends Elem[CCostedBuilderIso] {
  }
  // 4) constructor and deconstructor
  class CCostedBuilderCompanionCtor extends CompanionDef[CCostedBuilderCompanionCtor] with CCostedBuilderCompanion {
    def resultType = CCostedBuilderCompanionElem
    override def toString = "CCostedBuilderCompanion"
    @scalan.OverloadId("fromData")
    def apply(p: Ref[CCostedBuilderData]): Ref[CCostedBuilder] = {
      isoCCostedBuilder.to(p)
    }

    @scalan.OverloadId("fromFields")
    def apply(): Ref[CCostedBuilder] =
      mkCCostedBuilder()

    def unapply(p: Ref[CostedBuilder]) = unmkCCostedBuilder(p)
  }
  lazy val CCostedBuilderRef: Ref[CCostedBuilderCompanionCtor] = new CCostedBuilderCompanionCtor
  lazy val RCCostedBuilder: CCostedBuilderCompanionCtor = unrefCCostedBuilderCompanion(CCostedBuilderRef)
  implicit def unrefCCostedBuilderCompanion(p: Ref[CCostedBuilderCompanionCtor]): CCostedBuilderCompanionCtor = {
    if (p.rhs.isInstanceOf[CCostedBuilderCompanionCtor])
      p.rhs.asInstanceOf[CCostedBuilderCompanionCtor]
    else
      unrefDelegate[CCostedBuilderCompanionCtor](p)
  }

  implicit case object CCostedBuilderCompanionElem extends CompanionElem[CCostedBuilderCompanionCtor]

  implicit def unrefCCostedBuilder(p: Ref[CCostedBuilder]): CCostedBuilder = {
    if (p.rhs.isInstanceOf[CCostedBuilder])
      p.rhs.asInstanceOf[CCostedBuilder]
    else
      unrefDelegate[CCostedBuilder](p)
  }

  implicit class ExtendedCCostedBuilder(p: Ref[CCostedBuilder]) {
    def toData: Ref[CCostedBuilderData] = {
      isoCCostedBuilder.from(p)
    }
  }

  // 5) implicit resolution of Iso
  implicit def isoCCostedBuilder: Iso[CCostedBuilderData, CCostedBuilder] =
    reifyObject(new CCostedBuilderIso())

  def mkCCostedBuilder
    (): Ref[CCostedBuilder] = {
    new CCostedBuilderCtor()
  }
  def unmkCCostedBuilder(p: Ref[CostedBuilder]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: CCostedBuilderElem @unchecked =>
      Some(())
    case _ =>
      None
  }
} // of object CCostedBuilder
  registerEntityObject("CCostedBuilder", CCostedBuilder)

  registerModule(ConcreteCostsModule)

  // manual fix
  override protected def onReset(): Unit = {
    super.onReset()
    CCostedPrim._isoCCostedPrimMemo.reset()
  }
}

object ConcreteCostsModule extends scalan.ModuleInfo("special.collection", "ConcreteCosts")
}

trait ConcreteCostsModule extends special.collection.impl.ConcreteCostsDefs {self: Library =>}

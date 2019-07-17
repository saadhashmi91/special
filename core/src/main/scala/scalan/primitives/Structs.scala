package scalan.primitives

import scalan._
import scala.reflect.runtime.universe._
import scalan.util.CollectionUtil
import OverloadHack._
import scalan.compilation.GraphVizConfig
import special.Types

/**
 The code is inspired by LMS structs and is used in Scalan with the same semantics
 in order to easily translate operations to the equivalents via LmsBridge.
 Their usage in Scalan is limited to be consistent with functional semantics of Scalan.
 Don't expect everything possible in LMS to be also possible in Scalan in the same way.
 There are changes in the code:
 - Sym -> Exp
 - Manifest -> Elem
 - infix -> implicit class
 - no SourceContext, withPos
 - mirroring implemented in Scalan way (though consistent with LMS)
 */
trait Structs extends StructItemsModule with StructKeysModule { self: Scalan =>
  import IsoUR._
  // TODO consider if T type parameter is needed here and for AbstractStruct
  // It's only useful if we'll have some static typing on structs later (Shapeless' records?)
  abstract class StructTag[T <: Struct](implicit val typeTag: TypeTag[T]) extends Product {
    override def equals(other: Any): Boolean =
      !!!("StructTag.equals must be overridden so that the outer instances aren't compared")
  }
  case class SimpleTag[T <: Struct](name: String)(implicit typeTag: TypeTag[T]) extends StructTag[T] {
    override def equals(other: Any) = other match {
      case tag: Structs#SimpleTag[_] => name == tag.name && typeTag == tag.typeTag
      case _ => false
    }
  }
  object SimpleTag {
    def apply[T <: Struct](implicit tag: TypeTag[T]): SimpleTag[T] = SimpleTag[T](tag.tpe.typeSymbol.name.toString)
  }
  lazy val defaultStructTag = SimpleTag[Struct]

  protected def baseStructName(tag: StructTag[_]) = tag match {
    case `defaultStructTag` => ""
    case SimpleTag(name) => s"$name "
    // Intentionally no case _, add something here or override when extending StructTag!
  }

  type StructField = (String, Rep[Any])
  trait Struct {
    def tag: StructTag[_]
    def fields: Seq[StructField]
  }
  type RStruct = Rep[Struct]

  import Liftables._
  import scala.reflect.{ClassTag, classTag}

  type SStruct = special.collection.Coll[Any]

  case class StructConst[T <: Struct](constValue: SStruct, _selfType: StructElem[T])
        extends AbstractStruct[T] with LiftedConst[SStruct, T] {
    override lazy val selfType = _selfType
    def tag = _selfType.structTag
    val fields: Seq[(String, Rep[Any])] =
      constValue.toArray.zip(_selfType.fields).map { case (v, (fn, e)) => (fn, toRep(v)(e.asElem[Any])) }
    def liftable = liftableStruct(_selfType)
  }

  case class LiftableStruct[T <: Struct](eW: Elem[T]) extends Liftable[SStruct, T] {
    val sourceClassTag = classTag[SStruct]
    val sourceType = eW match {
      case se: StructElem[_] =>
        val (names, elems) = se.fields.unzip
        val types = elems.map(e => e.liftable.sourceType)
        Types.structRType(names.toArray, types.toArray)
      case _ =>
        !!!(s"Invalid argument of LiftableStruct($eW)")
    }
    def lift(x: SStruct): Rep[T] = StructConst(x, eW)
    def unlift(w: Rep[T]): SStruct = w match {
      case Def(StructConst(x: SStruct, _)) => x
      case _ => unliftError(w)
    }
  }

  implicit def liftableStruct[T <: Struct](implicit e: Elem[T]): Liftable[SStruct, T] =
    LiftableStruct(e)

  def liftStruct[T <: Struct](x: SStruct)(implicit eT: Elem[T]): Rep[T] = StructConst(x, eT)

  case class StructElem[T <: Struct](structTag: StructTag[T], fields: Seq[(String, Elem[_])]) extends Elem[T] {
    lazy val tag = structTag.typeTag

    override def liftable: Liftables.Liftable[_, T] = asLiftable[SStruct, T](liftableStruct(this))

    protected def getDefaultRep =
      struct(structTag, fields.map { case (fn,fe) => (fn, fe.defaultRepValue) }: _*)
    def get(fieldName: String): Option[Elem[_]] = fields.find(_._1 == fieldName).map(_._2)
    def apply(fieldIndex: Int): Elem[_] = fields(fieldIndex)._2
    def apply(fieldName: String): Elem[_] = get(fieldName).getOrElse {
      !!!(s"""Field $fieldName not found in struct $fieldsString""")
    }
    def fieldNames = fields.map(_._1)
    def fieldElems: Seq[Elem[_]] = fields.map(_._2)
    def isEqualType(tuple: Seq[Elem[_]]) = {
      fields.length == tuple.length && fields.zip(tuple).forall { case ((fn,fe), e) => fe == e }
    }
    override def getName(f: TypeDesc => String) =
      baseStructName(structTag) + fieldsString(f)
    def fieldsString(f: TypeDesc => String): String =
      "{" + fields.map { case (fn,fe) => s"$fn: ${f(fe)}" }.mkString("; ") + "}"
    lazy val fieldsString: String = fieldsString(_.name)
    def findFieldIndex(fieldName: String): Int = fields.iterator.map(_._1).indexOf(fieldName)

    override def buildTypeArgs = TypeArgs()
  }
  implicit def StructElemExtensions[T <: Struct](e: Elem[T]): StructElem[T] = e.asInstanceOf[StructElem[T]]

  /** Replaces a root tree of `PairElem`s in the given element `e` with `StructElem`s.
    * All other types are considered as leaves.
    * @return new StructElem if `e` is `PairElem` otherwise returns `e`.
    */
  def toStructElemShallow[T](e: Elem[T]): Elem[_] = e match {
    case pe: PairElem[a,b] =>
      tupleStructElement(toStructElemShallow(pe.eFst), toStructElemShallow(pe.eSnd))
    case _ => e
  }

  def structElement[T <: Struct](tag: StructTag[T], fields: Seq[(String, Elem[_])]): StructElem[T] =
    if (cacheElems)
      cachedElemByClass(tag, fields)(classOf[StructElem[T]])
    else
      StructElem(tag, fields)

  def structElement(fields: Seq[(String, Elem[_])]): StructElem[Struct] =
    structElement(defaultStructTag, fields)

  def structElementFor[T <: Struct : TypeTag](fields: Seq[(String, Elem[_])]): StructElem[T] =
    structElement(SimpleTag[T], fields)
  /**
    * Get tuple field name by index
    */
  def tupleFN(fieldIndex: Int) = s"_${fieldIndex + 1}"

  def tupleStructElement(fieldElems: Elem[_]*)(implicit o: Overloaded1): StructElem[Struct] = {
    val fields = fieldElems.zipWithIndex.map { case (f, i) => tupleFN(i) -> f }
    // TODO refactor: add tupleTag(n)
    structElement(defaultStructTag, fields)
  }

  def tuple2StructElement[A:Elem, B:Elem]: StructElem[Struct] =
    tupleStructElement(element[A], element[B])

  def tuple3StructElement[A:Elem, B:Elem, C:Elem]: StructElem[Struct] =
    tupleStructElement(element[A], element[B], element[C])

  case class StructToPairIso[A1, A2, B1, B2](iso1: Iso[A1, B1], iso2: Iso[A2, B2])
    extends IsoUR[Struct, (B1, B2)] {
    override def transform(t: Transformer) = StructToPairIso(t(iso1), t(iso2))
    override def equals(other: Any) = other match {
      case iso: Structs#StructToPairIso[_, _, _, _] =>
        (this eq iso) || (iso1 == iso.iso1 && iso2 == iso.iso2)
      case _ => false
    }

    implicit def eA1 = iso1.eFrom
    implicit def eA2 = iso2.eFrom
    implicit def eB1 = iso1.eTo
    implicit def eB2 = iso2.eTo
    lazy val eFrom = tuple2StructElement(iso1.eFrom, iso2.eFrom)
    lazy val eTo = element[(B1, B2)]
    lazy val selfType = new ConcreteIsoElem[Struct, (B1, B2), StructToPairIso[A1, A2, B1, B2]](eFrom, eTo).
      asElem[IsoUR[Struct, (B1, B2)]]

    override def from(p: Rep[(B1, B2)]) =
      struct(tupleFN(0) -> iso1.from(p._1), tupleFN(1) -> iso2.from(p._2))

    override def to(struct: Rep[Struct]) = {
      Pair(iso1.to(struct.getUnchecked[A1](tupleFN(0))), iso2.to(struct.getUnchecked[A2](tupleFN(1))))
    }
  }

  def structToPairIso[A1, A2, B1, B2](iso1: Iso[A1, B1], iso2: Iso[A2, B2]): Iso[Struct, (B1, B2)] =
    reifyObject(StructToPairIso[A1, A2, B1, B2](iso1, iso2))

  def structToPairIso[A:Elem,B:Elem]: Iso[Struct, (A, B)] = structToPairIso[A,B,A,B](identityIso[A], identityIso[B])
  def structToPairIso[A,B](pe: Elem[(A,B)]): Iso[Struct, (A, B)] = structToPairIso[A,B](pe.eFst, pe.eSnd)

  case class StructIso[S <: Struct, T <: Struct](eFrom: StructElem[S], eTo: StructElem[T], itemIsos: Seq[Iso[_,_]])
    extends IsoUR[S, T] {
    assert(eFrom.isEqualType(itemIsos.map(_.eFrom)))
    assert(eTo.isEqualType(itemIsos.map(_.eTo)))
    override def transform(t: Transformer) = StructIso[S, T](eFrom, eTo, itemIsos.map(t(_)))
    override def equals(other: Any) = other match {
      case iso: Structs#StructIso[_, _] =>
        (this eq iso) || (eFrom == iso.eFrom && eTo == iso.eTo)
      case _ => false
    }

    override def from(y: Rep[T]) = {
      val items = eFrom.fields.zip(eTo.fields).zip(itemIsos).map {
        case (((fnS, feS), (fnT, feT)), iso: Iso[s,t] @unchecked) =>
          fnS -> iso.from(y.getUnchecked[t](fnT))
        case (_, nonIso) => !!!(s"Iso expected but found $nonIso", self, y, nonIso)
      }
      asRep[S](struct(items))
    }
    override def to(x: Rep[S]) = {
      val items = eFrom.fields.zip(eTo.fields).zip(itemIsos).map {
        case (((fnS, feS), (fnT, feT)), iso: Iso[s,t] @unchecked) =>
          fnT -> iso.to(x.getUnchecked[s](fnS))
        case (_, nonIso) => !!!(s"Iso expected but found $nonIso", self, x, nonIso)
      }
      asRep[T](struct(items))
    }

    lazy val selfType = new ConcreteIsoElem[S, T, StructIso[S, T]](eFrom, eTo).asElem[IsoUR[S, T]]
  }

  def structIso[S <: Struct, T <: Struct](eFrom: StructElem[S], eTo: StructElem[T], itemIsos: Seq[Iso[_,_]]): Iso[S, T] =
    reifyObject(StructIso(eFrom, eTo, itemIsos))

  implicit class StructOps(s: Rep[Struct]) {
    def getUntyped(index: Int): Rep[_] = field(s, index)
    def getUntyped(fieldName: String): Rep[_] = field(s, fieldName)
    def getUnchecked[A](fieldName: String): Rep[A] = asRep[A](field(s, fieldName))
    def get[A: Elem](fieldName: String): Rep[A] = {
      val value = getUnchecked[A](fieldName)
      assertElem(value, element[A])
      value
    }
    def tag: StructTag[_ <: Struct] = s.elem.asInstanceOf[StructElem[_ <: Struct]].structTag
    def fieldNames = s.elem.asInstanceOf[StructElem[_ <: Struct]].fieldNames
    def fields: Seq[StructField] = {
      fieldNames.map(name => (name, field(s, name)))
    }
    def mapFields(f: Rep[_] => Rep[_]) = {
      val newFields = fieldNames.map { name =>
        val fieldValue = field(s, name)
        val newValue = f(fieldValue)
        (name, newValue)
      }
      struct(tag, newFields)
    }
  }

  def struct(fields: StructField*)(implicit o: Overloaded1): Rep[Struct] = struct(fields)
  def struct(fields: Seq[StructField]): Rep[Struct] = struct(defaultStructTag, fields)
  def struct[T <: Struct](tag: StructTag[T], fields: StructField*)(implicit o: Overloaded1): Rep[T] =
    struct(tag, fields)
  def tupleStruct(items: Rep[_]*): Rep[Struct] = {
    val fields = items.zipWithIndex.map { case (f, i) => tupleFN(i) -> f }
    struct(defaultStructTag, fields)
  }

  case class Link(field: String, nestedField: String, nestedElem: Elem[_], flatName: String)

  case class FlatteningIso[T <: Struct](eTo: StructElem[T], flatIsos: Map[String, Iso[_,_]], links: Seq[Link])
      extends IsoUR[Struct,T] {
    override def transform(t: Transformer) = FlatteningIso(eTo, flatIsos.mapValues(t(_)), links)
    override def equals(other: Any) = other match {
      case iso: Structs#FlatteningIso[_] =>
        (this eq iso) || (eFrom == iso.eFrom && eTo == iso.eTo)
      case _ => false
    }

    val eFrom = tupleStructElement(links.map(_.nestedElem): _*)
    lazy val selfType = new ConcreteIsoElem[Struct, T, FlatteningIso[T]](eFrom, eTo).asElem[IsoUR[Struct, T]]

    val groups = links.groupBy(_.field)

    def to(x: Rep[Struct]) = {
      val items = eTo.fields.map { case (fn, fe) =>
        val g = groups(fn)
        flatIsos.get(fn) match {
          case Some(iso: Iso[a, _] @unchecked) =>
            val projectedStruct = struct(g.map {
              link => link.nestedField -> x.getUntyped(link.flatName)
            }: _*)
            val s = iso.to(asRep[a](projectedStruct))
            (fn, s)
          case _ =>
            assert(g.length == 1, s"Many fields $g can't relate to the single field $fn without iso")
            (fn, x.getUntyped(g(0).flatName))
        }
      }
      struct(eTo.structTag, items: _*)
    }

    def from(y: Rep[T]) = {
      val items = eTo.fields.flatMap { case (fn, fe) =>
        val g = groups(fn)
        flatIsos.get(fn) match {
          case Some(iso: Iso[_, a] @unchecked) =>
            val nestedStruct = asRep[Struct](iso.from(y.getUnchecked[a](fn)))
            // nestedStruct is guaranteed to be a Rep[Struct], because iso can be either IdentityIso on a struct or FlatteningIso
            g.map { link =>
              link.flatName -> nestedStruct.getUntyped(link.nestedField)
            }
          case _ =>
            List(g(0).flatName -> y.getUntyped(fn))
        }
      }
      struct(items: _*)
    }
  }

  /**
   * Flattens all subtrees of structs in [[e]].
   * Types other than structs are considered either as internal nodes or as leaves.
   * @param e descriptor of struct type
   * @return an isomorphism in which [[e]] is given by param and `eFrom` is flattened [[e]] preserving
   *         related order of the components
   */
  def getFlatteningIso[T](e: Elem[T]): Iso[_,T] = e match {
    // a == T, but Scala can't infer the type bound if T is used below
    case se: StructElem[a] @unchecked =>
      val flatIso = flatteningIso(se).asInstanceOf[Iso[_, T]]
      flatIso match {
        case Def(_: IdentityIso[T] @unchecked) =>
          flatIso
        case Def(_: FlatteningIso[T] @unchecked) =>
          flatIso.eFrom match {
            // TODO Actually, we currently know s == Struct. Is extra complexity needed?
            case eFrom: StructElem[s] =>
              val isos = eFrom.fields.map { case (fn,fe) => (fn, buildIso(fe, flatteningBuilder)) }
              val eFromNew = structElement(isos.map { case (fn, iso) => fn -> iso.eFrom })
              val sIso = reifyObject(new StructIso(eFromNew, eFrom, isos.map(_._2)))
              sIso >> flatIso.asInstanceOf[Iso[s,T]]
          }
      }
    case _ =>
      buildIso(e, flatteningBuilder)
  }

  val flatteningBuilder = new IsoBuilder { def apply[S](e: Elem[S]) = getFlatteningIso(e) }

  def flatteningIso[T <: Struct](eTo: StructElem[T]): Iso[_, T] = {
    val flatIsos: Map[String, Iso[_, _]] = eTo.fields.collect {
      case (fn, fe: StructElem[_]) => (fn, flatteningIso(fe))
    }.toMap

    if (flatIsos.isEmpty)
      return identityIso(eTo)

    // relate resulting field types by original field name
    val fromFields = eTo.fields.flatMap {
      case (fn, fe) =>
        flatIsos.get(fn) match {
          case Some(iso) =>
            iso.eFrom match {
              case flatElem: StructElem[_] =>
                flatElem.fields.map { case (nestedName, nestedE) => (fn, nestedName -> nestedE) }
              case _ => !!!(s"StructElem is expected as eFrom of flattened Iso $iso")
            }
          case None => List((fn, "" -> fe))
        }
    }

    val links =
      fromFields.zipWithIndex.map {
        case ((fn, (nestedN, nestedE)), i) => Link(fn, nestedN, nestedE, tupleFN(i))
      }

    val res: Iso[_, T] = reifyObject(FlatteningIso(eTo, flatIsos, links))
    res
  }

  def getStructToPairsIso[T](implicit e: Elem[T]): Iso[_,T] = (e match {
    case pe: PairElem[a,b] =>
      val iso1 = getStructToPairsIso(pe.eFst)
      val iso2 = getStructToPairsIso(pe.eSnd)
      val res = structToPairIso(iso1, iso2)
      res
    case _ =>
      buildIso(e, new IsoBuilder {
        def apply[S](e: Elem[S]) = {
          getStructToPairsIso(e)
        }
      })
  }).asInstanceOf[Iso[_,T]]


  def getStructWrapperIso[T](implicit e: Elem[T]): Iso[_,T] = {
    getStructToPairsIso(e) match {
      case iso: Iso[s,T] @unchecked =>
        val flatIso = getFlatteningIso[s](iso.eFrom)
        flatIso >> iso
    }
  }

  case class MergeIso[T <: Struct](eTo: StructElem[T]) extends IsoUR[Struct,T] {
    override def transform(t: Transformer) = MergeIso(eTo)
    override def equals(other: Any) = other match {
      case iso: MergeIso[_] =>
        (this eq iso) || (eFrom == iso.eFrom && eTo == iso.eTo)
      case _ => false
    }

    val eFrom = structElement(eTo.fields.flatMap {
      case (fn, fe: StructElem[_]) => fe.fields
      case (_, nonStructElem) => !!!(s"StructElem expected but found $nonStructElem", self)
    })

    lazy val selfType = new ConcreteIsoElem[Struct, T, MergeIso[T]](eFrom, eTo).asElem[IsoUR[Struct, T]]

    def to(x: Rep[Struct]) = {
      val items = eTo.fields.map {
        case (outerN, outerE: StructElem[_]) =>
          val s = struct(outerE.fields.map { case (innerN, innerE) => innerN -> x.getUntyped(innerN) })
          outerN -> s
        case (_, nonStructElem) => !!!(s"StructElem expected but found $nonStructElem", self, x)
      }
      struct(eTo.structTag, items: _*)
    }

    def from(y: Rep[T]) = {
      val items = eTo.fields.flatMap {
        case (outerN, outerE: StructElem[_]) =>
          val s = asRep[Struct](y.getUntyped(outerN))
          outerE.fields.map { case (innerN, innerE) => innerN -> s.getUntyped(innerN) }
        case (_, nonStructElem) => !!!(s"StructElem expected but found $nonStructElem", self, y)
      }
      struct(items: _*)
    }
  }

  def getStructMergeIso[T](implicit e: Elem[T]): Iso[_,T] = (e match {
    case se: StructElem[_] =>
      reifyObject(MergeIso(se.asElem[Struct]))
    case _ =>
      !!!(s"Don't know how merge non struct $e")
  }).asInstanceOf[Iso[_,T]]

  def pairifyStruct[A <: Struct](se: Elem[A]): Elem[_] = {
    CollectionUtil.foldRight[(String, Elem[_]), Elem[_]](se.fields)(_._2) { case ((fn,fe), e) => pairElement(fe, e) }
  }

  def unzipMany[T](tuple: Rep[_], list: List[T]): List[Rep[_]] = {
    val pair = asRep[(Any, Any)](tuple)
    list match {
      case Nil => List(tuple)
      case x :: Nil => List(tuple)
      case x :: y :: Nil => List(pair._1, pair._2)
      case x :: xs =>
        pair._1 :: unzipMany(pair._2, xs)
    }
  }

  case class PairifyIso[A, AS <: Struct](eTo: Elem[AS]) extends IsoUR[A, AS] {
    val eFrom: Elem[A] = pairifyStruct(eTo).asElem[A]
    override def transform(t: Transformer) = PairifyIso(eTo)

    def from(y: Rep[AS]) =  {
      val res = CollectionUtil.foldRight[String, Rep[_]](eTo.fieldNames)(y.getUntyped(_)) {
        case (fn, s) => Pair(y.getUntyped(fn), s)
      }
      asRep[A](res)
    }

    override def to(x: Rep[A]) = {
      val items = unzipMany(x, eTo.fields.toList)
      val fields = eTo.fieldNames.zip(items.map(asRep[Any](_)))
      asRep[AS](struct(fields))
    }

    override def equals(other: Any) = other match {
      case iso: PairifyIso[_,_] =>
        (this eq iso) || (eFrom == iso.eFrom && eTo == iso.eTo)
      case _ => false
    }
    lazy val selfType = new ConcreteIsoElem[A, AS, PairifyIso[A, AS]](eFrom, eTo).asElem[IsoUR[A, AS]]
  }

  def structWrapper[A,B](f: Rep[A => B]): Rep[Any => Any] = {
    val wrapperFun = (getStructWrapperIso[A](f.elem.eDom),
                      getStructWrapperIso[B](f.elem.eRange)) match {
      case (inIso: Iso[a, A] @unchecked, outIso: Iso[b, B] @unchecked) =>
        outIso.fromFun << f << inIso.toFun
    }
    asRep[Any => Any](wrapperFun)
  }
  def structWrapperIn[A,B](f: Rep[A => B]): Rep[Any => B] = {
    val inIso = getStructWrapperIso[A](f.elem.eDom)
    val wrapperFun = inIso.toFun >> f
    asRep[Any => B](wrapperFun)
  }
  def structWrapperOut[A,B](f: Rep[A => B]): Rep[A => Any] = {
    val outIso = getStructWrapperIso[B](f.elem.eRange)
    val wrapperFun = f >> outIso.fromFun
    asRep[A => Any](wrapperFun)
  }
  abstract class AbstractStruct[T <: Struct] extends Def[T] {
    def tag: StructTag[T]
    def fields: Seq[StructField]
    lazy val selfType = structElement(tag, fields.map { case (name, value) => (name, value.elem) })
  }

  object Struct {
    def unapply[T <: Struct](d: Def[T]): Option[(StructTag[T], Seq[StructField])] = d match {
      case s: AbstractStruct[T] => Some((s.tag, s.fields))
      case _ => None
    }
  }

  object Field {
    def unapply[T](d: Def[T]): Option[(Rep[Struct], String)] = d match {
      case FieldApply(struct, fieldName) => Some((struct, fieldName))
      case _ => None
    }
  }

  case class SimpleStruct[T <: Struct](tag: StructTag[T], fields: Seq[StructField]) extends AbstractStruct[T] {
    override def transform(t: Transformer): Def[T] = SimpleStruct(tag, fields.map { case (n, s) => (n, t(s)) })
  }
  case class FieldApply[T](struct: Rep[Struct], fieldName: String)
    extends BaseDef[T]()(struct.elem(fieldName).asElem[T]) {
    override def transform(t: Transformer): Def[T] = FieldApply(t(struct), fieldName)
  }

  case class FieldUpdate[S <: Struct, T](struct: Rep[S], fieldName: String, value: Rep[T]) extends AbstractStruct[S] {
    val tag = struct.elem.structTag
    val fields = struct.elem.fields.map { case (fn, _) =>
      if (fn == fieldName)
        (fieldName, value)
      else
        (fn, field(struct, fn))
    }
    override def transform(t: Transformer): Def[S] = FieldUpdate(t(struct), fieldName, t(value))
  }

  case class ProjectionStruct(struct: Rep[Struct], outFields: Seq[String]) extends AbstractStruct[Struct] {
    def tag = defaultStructTag
    val fields = outFields.map(fn => (fn, field(struct, fn)))
    override def transform(t: Transformer): Def[Struct] = ProjectionStruct(t(struct), outFields)
  }

  def struct[T <: Struct](tag: StructTag[T], fields: Seq[StructField]): Rep[T] = {
    val names = fields.map(_._1)
    assert(names.distinct.lengthCompare(fields.length) == 0, s"Fields of struct should be distinct but $names")
    SimpleStruct(tag, fields)
  }
  def field(struct: Rep[Struct], field: String): Rep[_] = {
    struct.elem match {
      case se: StructElem[a] =>
        //        val fieldElem = se(field)
        FieldApply[a](struct, field)
      case _ =>
        !!!(s"Attempt to get field $field from a non-struct ${struct.toStringWithType}", struct)
    }
  }
  def field(struct: Rep[Struct], fieldIndex: Int): Rep[_] = {
    val fieldName = struct.elem.fields(fieldIndex)._1
    field(struct, fieldName)
  }

  def updateField[S <: Struct](struct: Rep[S], fieldName: String, v: Rep[_]): Rep[S] = FieldUpdate[S,Any](struct, fieldName, v)
  def fields(struct: Rep[Struct], fields: Seq[String]): Rep[Struct] = ProjectionStruct(struct, fields)

  override protected def formatDef(d: Def[_])(implicit config: GraphVizConfig): String = d match {
    case SimpleStruct(tag, fields) =>
      s"${baseStructName(tag)}{${fields.map { case (fn, s) => s"$fn:$s" }.mkString("; ")}}"
    case ProjectionStruct(struct, outs) => s"$struct.{${outs.mkString(",")}}"
    case FieldUpdate(s, fn, v) => s"$s.$fn := $v"
    case FieldApply(struct, fn) => s"$struct.$fn"
    case _ => super.formatDef(d)
  }

  case class ViewStruct[A, B](source: Rep[A])(val iso: Iso[A, B])
    extends View[A, B] {
    override def transform(t: Transformer) = ViewStruct(t(source))(t(iso))
    override def toString = s"ViewStruct[${iso.eTo.name}]($source)"
    override def equals(other: Any) = other match {
      case v: ViewStruct[_, _] => source == v.source && iso.eTo == v.iso.eTo
      case _ => false
    }
  }


  object FieldGet {
    def unapply[T](d: FieldApply[T]): Option[Rep[T]] = d match {
      case FieldApply(Def(SimpleStruct(_, fs)), fn) =>
        val optItem = fs.find { case (n, _) => n == fn }
        optItem.map(x => asRep[T](x._2))
      case _ => None
    }
  }

  object SameStructAs {
    def unapply[A](d: Def[A]): Nullable[Rep[A]] = d match {
      case Struct(tag, fields) =>
        fields.headOption match {
          case Some((_, Def(Field(possibleSourceStruct, _)))) if d.selfType == possibleSourceStruct.elem =>
            val eachFieldComesFromPossibleSourceStruct = fields.forall {
              case (name, Def(Field(`possibleSourceStruct`, name1))) if name == name1 =>
                true
              case _ =>
                false
            }
            if (eachFieldComesFromPossibleSourceStruct)
              Nullable(asRep[A](possibleSourceStruct))
            else
              Nullable.None
          case _ => Nullable.None
        }
      case _ => Nullable.None
    }
  }

  def shouldUnpackTuples = currentPass.config.shouldUnpackTuples
  def shouldExtractFields = currentPass.config.shouldExtractFields
  def shouldSlice = currentPass.config.shouldSlice

  override def rewriteDef[T](d: Def[T]): Sym = d match {
    case FieldGet(v) if shouldExtractFields => v
    case SameStructAs(s) => s
    case _ => super.rewriteDef(d)
  }

  object StructsRewriter extends Rewriter {
    def apply[T](x: Rep[T]): Rep[T] = asRep[T](x match {
      case Def(FieldGet(v)) => v
      case _ => x
    })
  }
}

trait StructsEx extends Structs with BaseEx { self: ScalanEx =>

  override def unapplyViews[T](s: Rep[T]): Option[Unpacked[T]] = (s match {
    case Def(view: ViewStruct[a, b]) =>
      Some((view.source, view.iso))
    case _ =>
      super.unapplyViews(s)
  }).asInstanceOf[Option[Unpacked[T]]]

}


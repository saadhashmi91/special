package scalan

import java.lang.reflect.{Method, Constructor => Constr}
import java.util.{HashMap, Objects, Arrays, function}

import configs.syntax._
import com.typesafe.config.{ConfigFactory, Config}
import com.typesafe.scalalogging.LazyLogging
import scalan.OverloadHack.Overloaded1

import scala.annotation.implicitNotFound
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.{mutable, TraversableOnce}
import scala.collection.mutable.ListBuffer
import scala.reflect.{ClassTag, classTag}
import scalan.compilation.GraphVizConfig
import scalan.util.{ParamMirror, ReflectionUtil}
import debox.{Buffer => DBuffer, Set => DSet}

import scala.reflect.runtime.universe._
import spire.syntax.all.cfor
import supertagged.TaggedType

/**
  * The Base trait houses common AST nodes. It also manages a list of encountered Definitions which
  * allows for common sub-expression elimination (CSE).
  */
trait Base extends LazyLogging { scalan: Scalan =>
  type Rep[+A] = Exp[A]
  type |[+A, +B] = Either[A, B]
  type :=>[-A, +B] = PartialFunction[A, B]
  type RFunc[-A,+B] = Rep[Function1[A,B]]
  type RPair[+A, +B] = Rep[(A,B)]

  // Consider if extra data should be Seq[Any] instead (change name in this case)
  class StagingException(message: String, cause: Throwable, val syms: Seq[Rep[_]]) extends
    RuntimeException(stagingExceptionMessage(message, syms), cause) {
    def this(message: String, syms: Seq[Rep[_]]) = this(message, null, syms)
  }

  class NotImplementedStagingException(message: String, syms: Seq[Rep[_]]) extends StagingException(message, null, syms)

  def ??? : Nothing = ???("Missing or incomplete implementation")
  def ???(value: Any, syms: Rep[_]*): Nothing = throw new NotImplementedStagingException(value.toString, syms)

  def !!! : Nothing = !!!("should not be called")
  def !!!(msg: String, syms: Rep[_]*): Nothing = throw new StagingException(msg, syms)
  def !!!(msg: String, e: Throwable, syms: Rep[_]*): Nothing = throw new StagingException(msg, e, syms)

  @inline final def asRep[T](x: Rep[_]): Rep[T] = x.asInstanceOf[Rep[T]]

  implicit class RepExtension[A](x: Rep[A]) {
    def asValue: A = valueFromRep(x)
  }

  @inline implicit def liftToRep[A:Elem](x: A): Rep[A] = toRep(x)

  trait Def[+T] extends Product {
    /** Unique id of the node. Initially undefined, should be defined after Def is added to the graph.
      * Doesn't participate in equality of this Def.
      * Use only to provide global Def numbering. */
    private[scalan] var _nodeId: Int = SingleSym.freshId
    @inline def nodeId: Int = _nodeId

    def selfType: Elem[T @uncheckedVariance]

    private var _self: Rep[T @uncheckedVariance] = _
    def self: Rep[T] = {
      if (_self == null) _self = symbolOf(this)
      _self
    }

    def transform(t: Transformer): Def[T] =
      !!!(s"Cannot transfrom definition using transform($this)", self)

    override def equals(other: Any) = (this eq other.asInstanceOf[AnyRef]) || (other match {
      // check that nodes correspond to same operation, have the same type, and the same arguments
      // alternative would be to include Elem fields into case class
      case other: Base#Def[_] =>
        ({
          val cls1 = getClass
          val cls2 = other.getClass
          cls1 == cls2 || {
            def nameWithoutCGLib(clazz: Class[_]) = {
              val name = clazz.getName
              name.indexOf("$$EnhancerByCGLIB$$") match {
                case -1 => name
                case i => name.substring(0, i)
              }
            }

            cls1.getClassLoader == cls2.getClassLoader && nameWithoutCGLib(cls1) == nameWithoutCGLib(cls2)
          }
        } && productArity == other.productArity && {
          val len = productArity
          var i = 0
          var result = true
          while (result && i < len) {
            result = Objects.deepEquals(productElement(i), other.productElement(i))
            i += 1
          }
          result
        } && selfType.name == other.selfType.name)
      case _ => false
    })

    private var _hashCode: Int = 0
    override def hashCode = {
      if (_hashCode == 0) {
        val len = productArity
        var i = 0
        var result = 1
        while (i < len) {
          val element = productElement(i)
          val elementHashCode = element match {
            case null => 0
            case arr: Array[Object] => Arrays.deepHashCode(arr)
            case arr: Array[Int] => Arrays.hashCode(arr)
            case arr: Array[Long] => Arrays.hashCode(arr)
            case arr: Array[Float] => Arrays.hashCode(arr)
            case arr: Array[Double] => Arrays.hashCode(arr)
            case arr: Array[Boolean] => Arrays.hashCode(arr)
            case arr: Array[Byte] => Arrays.hashCode(arr)
            case arr: Array[Short] => Arrays.hashCode(arr)
            case arr: Array[Char] => Arrays.hashCode(arr)
            case _ => element.hashCode
          }
          result = 41 * result + elementHashCode
          i += 1
        }
        _hashCode = result
      }
      _hashCode
    }

    override def toString = {
      val sb = new StringBuilder
      sb.append(productPrefix)
      sb.append("(")
      val iterator = productIterator
      if (iterator.hasNext) {
        append(sb, iterator.next)
      }
      while (iterator.hasNext) {
        sb.append(", ")
        append(sb, iterator.next)
      }
      sb.append(")")
      sb.toString
    }

    private final def append(sb: StringBuilder, x: Any): Unit = {
      x match {
        case arr: Array[_] =>
          sb.append("Array(")
          if (arr.length > 0) {
            append(sb, arr(0))
            var i = 1
            while (i < arr.length) {
              sb.append(", ")
              append(sb, arr(i))
              i += 1
            }
          }
          sb.append(")")
        case s: String =>
          sb.append("\"")
          sb.append(s)
          sb.append("\"")
        case _ => sb.append(x)
      }
    }
  }

  object Def {
    def unapply[T](e: Rep[T]): Nullable[Def[T]] = def_unapply(e)
  }
  object && {
    def unapply[T](x: T): Option[(T,T)] = Some((x, x))
  }

  abstract class CompanionDef[T] extends Def[T] {
    override def productArity = 0
    override def productElement(n: Int) = !!!(s"productElement($n) called, but productArity = 0", self)
    override def canEqual(other: Any) = other.isInstanceOf[CompanionDef[_]]
    override def transform(t: Transformer) = this
  }

  object Liftables {
    trait LiftedConst[ST, T] extends Def[T] {
      def constValue: ST
      def liftable: Liftable[ST, T]
    }

    /** Describes lifting data values of type ST (Source Type) to IR nodes of the correspoding staged type T.
      * In general T is different type obtained by virtualization procedure from ST. */
    @implicitNotFound(msg = "Cannot find implicit for Liftable[${ST},${T}].")
    trait Liftable[ST, T] {
      def sourceType: RType[ST]
      def eW: Elem[T]
      def lift(x: ST): Rep[T]
      def unlift(w: Rep[T]): ST
      protected def unliftError(w: Rep[T]) =
        !!!(s"Cannot unlift simbol $w using $this")

      override def hashCode(): Int = eW.hashCode() + 1 // to make Elem and Liftable differ
      override def equals(obj: Any): Boolean = super.equals(obj) || (obj match {
        case other: Liftable[_,_] => other.eW == eW
        case _ => false
      })
      override def toString: String = s"Liftable($eW)"
    }

    @inline final def asLiftable[ST,T](l: Liftable[_,_]): Liftable[ST,T] = l.asInstanceOf[Liftable[ST,T]]
    
    @inline final def liftable[ST, T](implicit lT: Liftable[ST,T]) = lT

    /** Given data value of type `ST` and `Liftable` instance between `ST` and `T`,
      * produces `LiftedConst` node (some concrete implemenation) and returns it's symbol.
      * This is generic way to put any liftable data object into graph and then use
      * its symbol in other nodes. */
    @inline final def liftConst[ST,T](x: ST)(implicit lT: Liftable[ST,T]): Rep[T] = lT.lift(x)

    class BaseLiftable[T](implicit val eW: Elem[T], override val sourceType: RType[T]) extends Liftable[T, T] {
      def lift(x: T) = toRep(x)
      def unlift(w: Rep[T]) = w.asValue
    }

    class PairLiftable[SA,SB,A,B](implicit lA: Liftable[SA, A], lB: Liftable[SB, B]) extends Liftable[(SA,SB), (A,B)] {
      val eW: Elem[(A, B)] = pairElement(lA.eW, lB.eW)
      override val sourceType: RType[(SA, SB)] = RType.pairRType(lA.sourceType, lB.sourceType)

      def lift(x: (SA, SB)): Rep[(A, B)] = Pair(lA.lift(x._1), lB.lift(x._2))
      def unlift(w: Rep[(A, B)]): (SA, SB) = { val Pair(wa, wb) = w; (lA.unlift(wa), lB.unlift(wb)) }
    }

    case class FuncConst[SA,SB,A,B](constValue: SA => SB)(implicit lA: Liftable[SA, A], lB: Liftable[SB, B])
          extends BaseDef[A => B]()(funcElement(lA.eW, lB.eW))
             with LiftedConst[SA => SB, A => B] {
      val liftable = Liftables.liftable[SA => SB, A => B]
    }

    class FuncLiftable[SA,SB,A,B](implicit lA: Liftable[SA, A], lB: Liftable[SB, B]) extends Liftable[SA => SB, A => B] {
      val eW: Elem[A => B] = funcElement(lA.eW, lB.eW)
      override val sourceType = { RType.funcRType(lA.sourceType, lB.sourceType) }
      def lift(srcF: SA => SB): Rep[A => B] = FuncConst[SA,SB,A,B](srcF)
      def unlift(f: Rep[A => B]): SA => SB = f match {
        case Def(FuncConst(srcF)) => srcF.asInstanceOf[SA => SB]
        case _ => unliftError(f)
      }
    }

    implicit lazy val BooleanIsLiftable = asLiftable[Boolean,Boolean](BooleanElement.liftable)
    implicit lazy val ByteIsLiftable    = asLiftable[Byte,Byte](ByteElement.liftable)
    implicit lazy val ShortIsLiftable   = asLiftable[Short,Short](ShortElement.liftable)
    implicit lazy val IntIsLiftable     = asLiftable[Int,Int](IntElement.liftable)
    implicit lazy val LongIsLiftable    = asLiftable[Long,Long](LongElement.liftable)
    implicit lazy val StringIsLiftable  = asLiftable[String,String](StringElement.liftable)
    implicit lazy val FloatIsLiftable   = asLiftable[Float,Float](FloatElement.liftable)
    implicit lazy val DoubleIsLiftable  = asLiftable[Double,Double](DoubleElement.liftable)
    implicit lazy val UnitIsLiftable    = asLiftable[Unit,Unit](UnitElement.liftable)
    implicit lazy val CharIsLiftable    = asLiftable[Char,Char](CharElement.liftable)

    implicit def PairIsLiftable[SA,SB,A,B]
        (implicit lA: Liftable[SA, A], lB: Liftable[SB, B]): Liftable[(SA, SB), (A, B)] =
      new PairLiftable[SA,SB,A,B]

    implicit def FuncIsLiftable[SA,SB,A,B]
        (implicit lA: Liftable[SA, A], lB: Liftable[SB, B]): Liftable[SA => SB, A => B] =
      new FuncLiftable[SA,SB,A,B]
  }

  class EntityObject(val entityName: String)

  private[this] val entityObjects = AVHashMap[String, EntityObject](300)

  @inline def getEntityObject(name: String): Nullable[EntityObject] = {
    entityObjects.get(name)
  }

  protected def registerEntityObject(name: String, obj: EntityObject): Unit = {
     assert(!entityObjects.containsKey(name), s"EntityObject for entity $name already registered")
     entityObjects.put(name, obj)
  }

  val cacheElems = true
  val cachePairs = true

  /**
    * constants/symbols (atomic)
    */
  abstract class Exp[+T] {
    def elem: Elem[T @uncheckedVariance]
    def varName: String
    def rhs: Def[T]
    private[scalan] var isRec = false
    def isRecursive: Boolean = isRec
    private[scalan] def isRecursive_=(b: Boolean) = { isRec = b }
    private[scalan] def assignDef[B >: T](sym: Def[B]): Unit
    private[scalan] def assignDefFrom[B >: T](sym: Exp[B]): Unit

    def isPlaceholder: Boolean = rhs.isInstanceOf[Placeholder[_]]
    def isVar: Boolean = rhs.isInstanceOf[Variable[_]]
    def isConst: Boolean = rhs.isInstanceOf[Const[_]]
    def isCompanion: Boolean = elem.isInstanceOf[CompanionElem[_]]

    def toStringWithDefinition: String
    def toStringWithType = varName + ":" + elem.name
    def show(): Unit = show(defaultGraphVizConfig)
    def show(emitMetadata: Boolean): Unit = show(defaultGraphVizConfig.copy(emitMetadata = emitMetadata))
    def show(config: GraphVizConfig): Unit = showGraphs(this)(config)
  }

  type Sym = Exp[_]

  abstract class BaseDef[+T](implicit val selfType: Elem[T @uncheckedVariance]) extends Def[T]

  case class Const[T](x: T)(implicit val eT: Elem[T]) extends BaseDef[T] {
    override def transform(t: Transformer) = this
  }

  /** @param varId   is independent from nodeId, shouldn't be used as node id.*/
  case class Variable[T](varId: Int)(implicit eT: LElem[T]) extends Def[T] {
    def selfType: Elem[T] = eT.value
    override def transform(t: Transformer): Def[T] =
      !!!(s"Method transfrom should not be called on $this", self)
  }

  @inline def variable[T](implicit eT: LElem[T]): Rep[T] = Variable[T](SingleSym.freshId)

  /** Symbols may temporary refer to this node until their target node is updated. */
  case class Placeholder[T](eT: LElem[T]) extends Def[T] {
    def selfType: Elem[T] = eT.value
  }

  @inline def placeholder[T](implicit eT: LElem[T]): Rep[T] = SingleSym.freshSym[T](Placeholder[T](eT))

  abstract class Transformer {
    def apply[A](x: Rep[A]): Rep[A]
    def isDefinedAt(x: Rep[_]): Boolean
    def domain: Set[Rep[_]]
    def apply[A](xs: Seq[Rep[A]]): Seq[Rep[A]] = xs map (e => apply(e))
    def apply(xs: Seq[Any])(implicit o: Overloaded1): Seq[Any] =
      xs map (e => e match { case s: Rep[_] => apply(s); case _ => e })
    def apply[X,A](f: X=>Rep[A]): X=>Rep[A] = (z:X) => apply(f(z))
    def apply[X,Y,A](f: (X,Y)=>Rep[A]): (X,Y)=>Rep[A] = (z1:X,z2:Y) => apply(f(z1,z2))
    def onlySyms[A](xs: List[Rep[A]]): List[Rep[A]] = xs map (e => apply(e)) collect { case e: Rep[A] => e }
  }

  trait TransformerOps[Ctx <: Transformer] {
    def empty: Ctx
    def add[A](ctx: Ctx, kv: (Rep[A], Rep[A])): Ctx
    def merge(ctx1: Ctx, ctx2: Ctx): Ctx = ctx2.domain.foldLeft(ctx1) {
      case (t, s: Rep[a]) => add(t, (s, ctx2(s)))
    }
  }

  implicit class TransformerEx[Ctx <: Transformer](self: Ctx)(implicit ops: TransformerOps[Ctx]) {
    def +[A](kv: (Rep[A], Rep[A])) = ops.add(self, kv)
    def ++(kvs: Map[Rep[A], Rep[A]] forSome {type A}) = kvs.foldLeft(self)((ctx, kv) => ops.add(ctx, kv))
    def merge(other: Ctx): Ctx = ops.merge(self, other)
  }

  protected def stagingExceptionMessage(message: String, syms: Seq[Rep[_]]) = {
    // Skip syms already in the message, assume that's the only source for s<N>
    val symsNotInMessage = syms.map(_.toString).filterNot(message.contains)

    if (symsNotInMessage.isEmpty) {
      message
    } else {
      val between = if (message.isEmpty)
        ""
      else
        message.last match {
          // determine whether the message lacks ending punctuation
          case '.' | ';' | '!' | '?' => " "
          case _ => ". "
        }

      message + between + s"Sym${if (symsNotInMessage.length > 1) "s" else ""}: ${symsNotInMessage.mkString(", ")}"
    }
  }

  sealed trait OwnerParameter
  case object NoOwner extends OwnerParameter
  case object ScalanOwner extends OwnerParameter
  case class  EntityObjectOwner(obj: EntityObject) extends OwnerParameter

  private[this] case class ReflectedProductClass(constructor: Constr[_], paramMirrors: List[ParamMirror], hasScalanParameter: OwnerParameter)

  protected def getOwnerParameterType(constructor: Constr[_]): OwnerParameter = {
    val paramTypes = constructor.getParameterTypes
    val ownerParam =
      if (paramTypes.length == 0)
        NoOwner
      else {
        val firstParamClazz = paramTypes(0)
        if (firstParamClazz.getSuperclass == classOf[EntityObject]) {
          val className = firstParamClazz.getSimpleName
          val entityName = className.substring(0, className.length - 1)
          getEntityObject(entityName) match {
            case Nullable(obj) =>
              EntityObjectOwner(obj)
            case _ =>
              !!!(s"Unknown owner type $firstParamClazz")
          }
        } else {
          ScalanOwner
        }
      }
    ownerParam
  }

  import Liftables.LiftedConst

  /** Clone this definition transforming all symbols using `t`.
    * If new Def[A] is created, it is added to the graph with collapsing and rewriting.
    * Can be overriden following `stackable overrides` pattern (calling super at the end).
    * @param  t  mapping of symbols to symbols
    * @return  symbol of the logical clone. If `d` don't contain symbols, then d.self is returned. */
  def transformDef[A](d: Def[A], t: Transformer): Rep[A] = d match {
    case c: Const[_] => c.self
    case v: Variable[_] => v.self
    case comp: CompanionDef[_] => comp.self
    case lc if lc.isInstanceOf[LiftedConst[_,_]] => lc.self
    case _ =>
      val newD = d.transform(t)
      reifyObject(newD)
  }

  protected def transformProductParam(x: Any, t: Transformer): Any = x match {
    case e: Rep[_] => t(e)
    case seq: Seq[_] => seq.map(transformProductParam(_, t))
    case arr: Array[_] => arr.map(transformProductParam(_, t))
    case opt: Option[_] => opt.map(transformProductParam(_, t))
    case d: Def[_] => transformDef(d, t).rhs
    case x => x
  }

  private[scalan] def addOwnerParameter(ownerType: OwnerParameter, params: Seq[Any]): Seq[AnyRef] = {
    val finalParams = (ownerType match {
      case EntityObjectOwner(obj) => obj +: params
      case ScalanOwner => scalan +: params
      case NoOwner => params
    })
    finalParams.asInstanceOf[Seq[AnyRef]]
  }

  implicit def reifyObject[A](obj: Def[A]): Rep[A] = {
    toExp(obj, obj.self)
  }

  def reifyEffects[A](block: => Rep[A]): Rep[A] = block

  def toRep[A](x: A)(implicit eA: Elem[A]):Rep[A] = eA match {
    case _: BaseElem[_] => Const(x)
    case _: FuncElem[_, _] => Const(x)
    case pe: PairElem[a, b] =>
      val x1 = x.asInstanceOf[(a, b)]
      implicit val eA = pe.eFst
      implicit val eB = pe.eSnd
      Pair(toRep(x1._1), toRep(x1._2))
    case _ =>
      x match {
        // this may be called instead of reifyObject implicit in some cases
        case d: Base#Def[A @unchecked] => reifyObject(d.asInstanceOf[Def[A]])
        case _ => !!!(s"Don't know how to create Rep for $x with element $eA")
      }
  }

  def valueFromRep[A](x: Rep[A]): A = x match {
    case Def(Const(x)) => x
    case _ => delayInvoke
  }

  def def_unapply[T](e: Rep[T]): Nullable[Def[T]] = new Nullable(e.rhs)

  object ExpWithElem {
    def unapply[T](s: Rep[T]): Nullable[(Rep[T],Elem[T])] = Nullable((s, s.elem))
  }

  abstract class Stm // statement (links syms and definitions)

  trait TableEntry[+T] extends Stm {
    def sym: Rep[T]
    def rhs: Def[T]
    def isLambda = rhs.isInstanceOf[Lambda[_, _]]
  }

  trait TableEntryCompanion {
    def apply[T](sym: Rep[T], rhs: Def[T]): TableEntry[T]
    def apply[T](sym: Rep[T], rhs: Def[T], lam: Rep[_]): TableEntry[T]
  }

  def decompose[T](d: Def[T]): Option[Rep[T]] = None

  def flatMapWithBuffer[A, T](iter: Iterator[A], f: A => TraversableOnce[T]): List[T] = {
    // performance hotspot: this is the same as
    // iter.toList.flatMap(f(_)) but faster
    val out = new ListBuffer[T]
    while (iter.hasNext) {
      val e = iter.next()
      out ++= f(e)
    }
    out.result()
  }

  @inline def flatMapIterable[A, T](iterable: Iterable[A], f: A => TraversableOnce[T]) =
    flatMapWithBuffer(iterable.iterator, f)

  @inline def flatMapProduct[T](p: Product, f: Any => TraversableOnce[T]): List[T] = {
    val iter = p.productIterator
    flatMapWithBuffer(iter, f)
  }

  // regular data (and effect) dependencies
  def syms(e: Any): List[Rep[_]] = e match {
    case s: Rep[_] => new scala.collection.immutable.::(s, Nil)  // optimization of hot spot
    case s: Iterable[_] =>
      flatMapWithBuffer(s.iterator, syms)
    // All case classes extend Product!
    case p: Product =>
      flatMapProduct(p, syms)
    case _ => Nil
  }
  def dep(e: Rep[_]): List[Rep[_]] = e match {
    case Def(d) => syms(d)
    case _ => Nil
  }
  def dep(d: Def[_]): List[Rep[_]] = syms(d)

  implicit class ExpForSomeOps(symbol: Rep[_]) {
    def inputs: List[Rep[Any]] = dep(symbol)
    def getDeps: List[Rep[_]] = symbol match {
      case Def(g: AstGraph) => g.freeVars.toList
      case _ => this.inputs
    }

    /** Shallow dependencies don't look into branches of IfThenElse  */
    def getShallowDeps: List[Sym] = symbol match {
      case Def(IfThenElse(c, _, _)) => new scala.collection.immutable.::(c, Nil)  // optimization of hot spot
      case _ => getDeps
    }

    def isLambda: Boolean = symbol match {
      case Def(_: Lambda[_, _]) => true
      case _ => false
    }
  }

  def getDeps(d: Def[_]): List[Rep[_]] = d match {
    case g: AstGraph => g.freeVars.toList
    case _ => syms(d)
  }

  implicit class DefForSomeOps(d: Def[_]) {
    def getDeps: List[Rep[_]] = scalan.getDeps(d)
    def asDef[T] = d.asInstanceOf[Def[T]]
  }

  case class HasArg(predicate: Rep[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Def[T]] = {
      val args = dep(d)
      if (args.exists(predicate)) Some(d) else None
    }
  }

  case class FindArg(predicate: Rep[_] => Boolean) {
    def unapply[T](d: Def[T]): Option[Rep[_]] = {
      val args = dep(d)
      for { a <- args.find(predicate) } yield a
    }
  }

  val performViewsLifting: Boolean = true

  def rewrite[T](s: Rep[T]): Rep[_] = s match {
    case Def(d) =>
      if (performViewsLifting) {
        val v = rewriteViews(d)
        val res = if (v != null) v else rewriteDef(d)
        res
      } else
        rewriteDef(d)
    case _ => rewriteVar(s)
  }

  def rewriteViews[T](d: Def[T]): Rep[_] = null

  def rewriteDef[T](d: Def[T]): Rep[_] = null

  def rewriteVar[T](s: Rep[T]): Rep[_] = null


  /** Light weight stateless immutable reference to a graph node (Def[T]).
    * Two symbols are equal if they refer to the nodes with the same id,
    * which is due to Def unification means equal symbols refer to the same instance of Def.
    * */
  class SingleSym[+T] private[Base] (private var _rhs: Def[T @uncheckedVariance]) extends Exp[T] {
    override def elem: Elem[T @uncheckedVariance] = _rhs.selfType
    def rhs: Def[T] = _rhs

    private[scalan] def assignDefInternal[B >: T](d: Def[B]): Unit = {
      assert(_rhs.isInstanceOf[Placeholder[_]])
      assert(_rhs.nodeId > 0)
      val tab = _symbolTable
      val oldId = _rhs.nodeId
      if (tab(oldId) eq this) {
        tab.update(oldId, null)
      }
      _rhs = d.asInstanceOf[Def[T]]
    }

    private[scalan] def assignDef[B >: T](d: Def[B]): Unit = {
      assignDefInternal(d)
      updateSymbolTable(this, d)
    }

    private[scalan] def assignDefFrom[B >: T](sym: Exp[B]): Unit = {
      assignDefInternal(sym.rhs)
    }

    private var _adapter: T @uncheckedVariance = _
    def adapter: T @uncheckedVariance = _adapter
    def adapter_=(a: T @uncheckedVariance) = { _adapter = a }

    def varName = "s" + _rhs._nodeId
    override def toString = varName
    def toStringWithDefinition = toStringWithType + s" = ${_rhs}"

    override def equals(obj: scala.Any): Boolean = (this eq obj.asInstanceOf[AnyRef]) || (obj match {
      case other: SingleSym[_] => _rhs._nodeId == other.rhs._nodeId
      case _ => false
    })

    override def hashCode(): Int = _rhs._nodeId
  }

  /**
    * A Sym is a symbolic reference used internally to refer to graph nodes.
    */
  object SingleSym {
    private var currId = 0
    @inline final def freshId: Int = { currId += 1; currId }

    @inline final def freshSym[T](d: Def[T]): Rep[T] = {
      updateSymbolTable(null, d)
    }

    final def resetIdCounter() = { currId = 0 }
  }

  /** Create or find symbol in the table which refers to the given node.
    * The d.nodeId is the index in the _symbolTable which is DBuffer (backed by Array)
    * @return   new of existing symbol
    * @hotspot  the method should be allocation-free (make it sure by examining the generated Java code)
    */
  final def updateSymbolTable[T](s: Exp[T], d: Def[T]): Exp[T] = {
    val id = d.nodeId
    val tab = _symbolTable  // perf optimization
    val delta = id - tab.length
    if (delta < 0) {
      val sym = tab.apply(id)
      if (sym == null) {
        val newSym = if (s == null) new SingleSym(d) else s  // we really want this allocation to happen only when necessary
        tab.update(id, newSym)
        newSym
      } else {
        // don't create new symbol, but check invariant condition on existing one
        assert(sym.rhs.nodeId == id, s"Each symbol should refer to correct node, but was $sym -> ${sym.rhs}")
        sym.asInstanceOf[Exp[T]]
      }
    } else {
      // grow table
      cfor(0)(_ < delta, _ + 1) { _ => tab.append(null) }
      val sym = if (s == null) new SingleSym(d) else s
      tab += sym
      assert(tab.length == id + 1)
      sym
    }
  }

  @inline final def getSym(id: Int): Sym = _symbolTable(id)

  @inline final def symbolOf[T](d: Def[T]): Rep[T] = SingleSym.freshSym[T](d)

  case class TableEntrySingle[T](sym: Rep[T], rhs: Def[T], lambda: Option[Rep[_]]) extends TableEntry[T]

  val TableEntry: TableEntryCompanion = new TableEntryCompanion {
    def apply[T](sym: Rep[T], rhs: Def[T]) = new TableEntrySingle(sym, rhs, None)
    def apply[T](sym: Rep[T], rhs: Def[T], lam: Rep[_]) = new TableEntrySingle(sym, rhs, Some(lam))
  }

  val nInitialDefs = 10000
  private[this] val _symbolTable: DBuffer[Sym] = DBuffer.ofSize(nInitialDefs)
//  private[this] var defToGlobalDefs: java.util.HashSet[Def[_]] = new java.util.HashSet[Def[_]](nInitialDefs) //DSet.ofSize(nInitialDefs) //AVHashMap[Def[_], TableEntry[_]](nInitialDefs)
  private[this] var defToGlobalDefs = AVHashMap[Def[_], Def[_]](nInitialDefs)

  protected var globalThunkSym: Rep[_] = placeholder[Int] // we could use any type here

  def defCount = defToGlobalDefs.hashMap.size

  private val _intZero = MutableLazy(0: Rep[Int])
  @inline final def IntZero = _intZero.value

  def resetContext() = {
    defToGlobalDefs = AVHashMap[Def[_], Def[_]](nInitialDefs)
    _symbolTable.clear()
    _symbolTable.splice(0, DBuffer.ofSize[Sym](nInitialDefs))
    SingleSym.resetIdCounter()
    globalThunkSym = placeholder[Int]
    tuplesCache.clear()
    _intZero.reset()
    onReset()
  }

  /** Called during resetContext() operation after the core context state has been reset.
   * Derived classes can override to define application specific initialization.
   * Don't forget to call super method in the beginning of your overriding method. */
  protected def onReset(): Unit = {
  }

  def findGlobalDefinition[T](d: Def[T]): Rep[T] = {
    val existingOpt = defToGlobalDefs.get(d)
    if (existingOpt.isDefined)
      existingOpt.get.self.asInstanceOf[Rep[T]]
    else
      null
  }

  /** Lookup `d` in the heap of nodes. If the lookup is successfull, then `d.self`
    * reference is returned. If the node is not found in the heap, then it is added
    * and `d.self` reference is returned.
    * @param  d       node to be added to the head of nodes
    * @param  newSym  producer of the reference to be used as the reference to `d` node.
    * @return         return a reference to `d` node in the heap
    * @hotspot */
  def findOrCreateDefinition[T](d: Def[T], newSym: => Rep[T]): Rep[T] = {
    val optScope = thunkStack.top
    var sym = optScope match {
      case Nullable(scope) =>
        scope.findDef(d)
      case _ =>
        findGlobalDefinition(d)
    }
    if (sym == null) {
      sym = createDefinition(optScope, newSym, d)
    }
//    assert(te.rhs == d, s"${if (te !) "Found" else "Created"} unequal definition ${te.rhs} with symbol ${te.sym.toStringWithType} for $d")
    sym
  }

  def createDefinition[T](s: Rep[T], d: Def[T]): Rep[T] =
    createDefinition(thunkStack.top, s, d)

  protected def createDefinition[T](optScope: Nullable[ThunkScope], s: Rep[T], d: Def[T]): Rep[T] = {
    assert(_symbolTable(d.nodeId).rhs.nodeId == d.nodeId)
    assert(s.rhs eq d, s"Inconsistent Sym -> Def pair $s -> $d")
    optScope match {
      case Nullable(scope) =>
        scope += s
      case _ =>
        defToGlobalDefs.put(d, d)
    }
    s
  }

  /**
    * Updates the universe of symbols and definitions, then rewrites until fix-point
    * @param d A new graph node to add to the universe
    * @param newSym A symbol that will be used if d doesn't exist in the universe
    * @tparam T
    * @return The symbol of the graph which is semantically(up to rewrites) equivalent to d
    */
  protected[scalan] def toExp[T](d: Def[T], newSym: => Rep[T]): Rep[T] = {
    var res = findOrCreateDefinition(d, newSym)
    var currSym = res
    var currDef = d
    do {
      currSym = res
      val ns = rewrite(currSym).asInstanceOf[Rep[T]]
      ns match {
        case null =>
          currDef = null
        case Def(someOtherD) =>
          res = ns
          currDef = someOtherD
        case _ =>
          res = ns
          currDef = null
      }
    } while (res != currSym && currDef != null)
    res
  }

  object IdSupply {
    private var _nextId = 0

    def nextId = {
      _nextId += 1
      _nextId
    }
  }
}


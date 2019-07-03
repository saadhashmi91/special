/**
 * Shamelessly taken from https://github.com/namin/lms-sandbox
 */
package scalan

import java.lang.reflect.{InvocationTargetException, Method}
import java.util.Objects

import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.{Success, Try}
import org.objenesis.ObjenesisStd
import net.sf.cglib.proxy.{InvocationHandler, Factory, Enhancer}
import scalan.compilation.{GraphVizConfig, GraphVizExport}
import scalan.util.{ReflectionUtil, StringUtil, ScalaNameUtil}

import scala.collection.mutable.ArrayBuffer

trait Proxy extends Base with Metadata with GraphVizExport { self: Scalan =>
  import IsoUR._
  
  def getStagedFunc(name: String): Rep[_] = {
    val clazz = this.getClass
    val f = clazz.getDeclaredMethod(name)
    f.invoke(this).asInstanceOf[Rep[_]]
  }

  def delayInvoke = throw new DelayInvokeException

  // call mkMethodCall instead of constructor
  case class MethodCall private[Proxy](receiver: Sym, method: Method, args: List[AnyRef], neverInvoke: Boolean)
                                      (val selfType: Elem[Any], val isAdapterCall: Boolean = false) extends Def[Any] {

    override def toString = {
      val methodStr = method.toString.replace("java.lang.", "").
        replace("public ", "").replace("abstract ", "")
      s"MethodCall($receiver, $methodStr, [${args.mkString(", ")}], $neverInvoke)"
    }

    def tryInvoke: InvokeResult =
      if (neverInvoke && !isAdapterCall)
        InvokeImpossible
      else
        findInvokableMethod[InvokeResult](receiver, method, args.toArray) {
          res => InvokeSuccess(res.asInstanceOf[Sym])
        } { InvokeFailure(_) } { InvokeImpossible }

    //TODO optimize
    import scalan.util.CollectionUtil.TraversableOps
    override def equals(other: Any): Boolean = (this eq other.asInstanceOf[AnyRef]) || {
      other match {
        case other: MethodCall =>
          receiver == other.receiver &&
          method == other.method &&
          selfType.name == other.selfType.name &&
          neverInvoke == other.neverInvoke &&
          isAdapterCall == other.isAdapterCall &&
          args.length == other.args.length &&
          args.sameElements2(other.args) // this is required in case method have T* arguments
        case _ => false
      }
    }

    override lazy val hashCode: Int = {
      var h = receiver.hashCode() * 31 + method.hashCode()
      h = h * 31 + selfType.name.hashCode
      h = h * 31 + (if(neverInvoke) 1 else 0)
      h = h * 31 + (if(isAdapterCall) 1 else 0)
      h = h * 31 + args.hashCode()
      h
    }
  }

  case class NewObject[A](eA: Elem[A], args: List[Any], neverInvoke: Boolean) extends BaseDef[A]()(eA) {
    override def transform(t: Transformer) = NewObject(eA, t(args).toList, neverInvoke)
  }

  override def transformDef[A](d: Def[A], t: Transformer): Rep[A] = d match {
    // not the same as super because mkMethodCall can produce a more precise return type
    case mc @ MethodCall(receiver, method, args, neverInvoke) =>
      val args1 = args.map(transformProductParam(_, t).asInstanceOf[AnyRef])
      val receiver1 = t(receiver)
      // in the case neverInvoke is false, the method is invoked in rewriteDef
      mkMethodCall(receiver1, method, args1, neverInvoke, mc.isAdapterCall, mc.selfType).asInstanceOf[Rep[A]]
    case _ => super.transformDef(d, t)
  }

  def mkMethodCall(receiver: Sym, method: Method, args: List[AnyRef], neverInvoke: Boolean): Sym = {
    val resultElem = try {
      getResultElem(receiver, method, args)
    } catch {
      case e: Exception =>
        throwInvocationException("getResultElem for", e, receiver, method, args)
    }
    mkMethodCall(receiver, method, args, neverInvoke, false, resultElem)
  }

  def mkMethodCall(receiver: Sym, method: Method, args: List[AnyRef],
                   neverInvoke: Boolean, isAdapterCall: Boolean, resultElem: Elem[_]): Sym = {
    reifyObject(MethodCall(receiver, method, args, neverInvoke)(resultElem.asElem[Any], isAdapterCall))
  }

  @tailrec
  private def baseCause(e: Throwable): Throwable = e match {
    case e: java.lang.reflect.UndeclaredThrowableException => baseCause(e.getCause)
    case e: net.sf.cglib.proxy.UndeclaredThrowableException => baseCause(e.getCause)
    case e: InvocationTargetException => baseCause(e.getCause)
    case e: ExceptionInInitializerError => baseCause(e.getCause)
    case e => e
  }

  def canBeInvoked(mc: MethodCall) = {
    val okFlags = !(mc.neverInvoke && !mc.isAdapterCall)
    val should = shouldInvoke(mc.receiver.rhs, mc.method, mc.args.toArray)
    okFlags && should
  }

  override protected def nodeColor(td: TypeDesc, d: Def[_])(implicit config: GraphVizConfig) = d match {
    case mc: MethodCall if !canBeInvoked(mc) => "blue"
    case no: NewObject[_] if no.neverInvoke => "darkblue"
    case _ => super.nodeColor(td, d)
  }

  override protected def formatDef(d: Def[_])(implicit config: GraphVizConfig): String = d match {
    case MethodCall(obj, method, args, _) =>
      val methodCallStr =
        s"${ScalaNameUtil.cleanScalaName(method.getName)}(${args.mkString(", ")})"
      if (obj.isCompanion) {
        s"$obj.$methodCallStr"
      } else {
        val className = ScalaNameUtil.cleanNestedClassName(method.getDeclaringClass.getName)
        s"$obj.$className.$methodCallStr"
      }
    case NewObject(eA, args, _) =>
      val className = ScalaNameUtil.cleanNestedClassName(eA.runtimeClass.getName)
      s"new $className(${args.mkString(", ")})"
    case _ => super.formatDef(d)
  }

  override def rewriteDef[T](d: Def[T]): Sym = d match {
    // Rule: (if(c) t else e).m(args) ==> if (c) t.m(args) else e.m(args)
    case MethodCall(Def(IfThenElse(cond, t, e)), m, args, neverInvoke) =>
      def copyMethodCall(newReceiver: Sym) =
        mkMethodCall(newReceiver, m, args, neverInvoke)

      ifThenElse(cond,
        copyMethodCall(t),
        copyMethodCall(e)
      )

    case call @ MethodCall(receiver, m, args, neverInvoke) =>
      call.tryInvoke match {
        // Rule: receiver.m(args) ==> body(m).subst{xs -> args}
        case InvokeSuccess(res) => res
        case InvokeFailure(e) if !e.isInstanceOf[DelayInvokeException] =>
          throwInvocationException("Method invocation in rewriteDef", e, receiver, m, args)
        case InvokeImpossible =>
          val res = rewriteNonInvokableMethodCall(call)
          if (res != null) res
          else
            super.rewriteDef(d)
      }

    case _ => super.rewriteDef(d)
  }

  /** This method is called for each MethodCall node which is about to be added to the graph.
    * This means `mc` has been examined by all the rewrite rules, but has not need rewritten.
    * Now, if this method returns null, then mc will be added to the graph.
    * However, in this method, `mc` can be examined by a second set of RW rules
    * (kind of lower priority rules). These rules kind of context dependent, because at this
    * point we know that the first RW set didn't triggered any rewrite. */
  def rewriteNonInvokableMethodCall(mc: MethodCall): Rep[_] = null

  def methodCallEx[A](receiver: Rep[_], m: Method, args: List[AnyRef]): Rep[A] =
    mkMethodCall(receiver, m, args, true).asInstanceOf[Rep[A]]

  def newObjEx[A](args: Any*)(implicit eA: Elem[A]): Rep[A] = {
    reifyObject(NewObject[A](eA, args.toList, true))
  }

  private lazy val proxies = scala.collection.mutable.Map.empty[(Rep[_], ClassTag[_]), AnyRef]
  private lazy val objenesis = new ObjenesisStd

  def proxyOps[Ops <: AnyRef](x: Rep[Ops])(implicit ct: ClassTag[Ops]): Ops =
    x match {
      case Def(Const(c)) => c
      case _ => getProxy(x, ct)
    }

  private def getProxy[Ops](x: Rep[Ops], ct: ClassTag[Ops]) = {
    val proxy = proxies.getOrElseUpdate((x, ct), {
      val clazz = ct.runtimeClass
      val e = new Enhancer
      e.setClassLoader(clazz.getClassLoader)
      e.setSuperclass(clazz)
      e.setCallbackType(classOf[ExpInvocationHandler[_]])
      val proxyClass = e.createClass().asSubclass(classOf[AnyRef])
      val proxyInstance = objenesis.newInstance(proxyClass).asInstanceOf[Factory]
      proxyInstance.setCallback(0, new ExpInvocationHandler(x))
      proxyInstance
    })
    proxy.asInstanceOf[Ops]
  }

  def isDescMethod(m: Method): Boolean = {
    classOf[TypeDesc].isAssignableFrom(m.getReturnType)
  }

  private def findInvokableMethod[A](receiver: Sym, m: Method, args: Array[AnyRef])
                                    (onInvokeSuccess: AnyRef => A)
                                    (onInvokeException: Throwable => A)
                                    (onNoMethodFound: => A): A = {
    def tryInvoke(obj: Any, m: Method) = try {
        val res = m.invoke(obj, args: _*)
        onInvokeSuccess(res)
      } catch {
        case e: Exception => onInvokeException(baseCause(e))
      }
    def tryInvokeElem(e: Elem[_]) = try {
        findTypeDescPropertyOfElem(e, m) match {
          case Some(elemMethod) =>
            tryInvoke(e, elemMethod)
          case _ =>
            onNoMethodFound
        }
      } catch {
        case e: Exception => onInvokeException(baseCause(e))
      }
    val d = receiver.rhs
    def findMethodLoop(m: Method): Option[Method] =
      if (shouldInvoke(d, m, args))
        Some(m)
      else
        None

    findMethodLoop(m) match {
      case Some(m1) =>
        tryInvoke(d, m1)
      case None =>
        onNoMethodFound
    }
  }

  protected def findTypeDescPropertyOfElem(e: Elem[_], nodeMethod: Method) = {
    try {
      val elemClass = e.getClass
      val elemMethod = elemClass.getMethod(nodeMethod.getName, nodeMethod.getParameterTypes: _*)
      val returnType = getMethodReturnTypeFromElem(e, elemMethod)
      returnType match {
        case TypeRef(_, sym, List(tpe1)) if scalan.meta.ScalanAst.TypeDescTpe.DescNames.contains(sym.name.toString) =>
          Some(elemMethod)
        case _ => None
      }
    } catch {
      case _: NoSuchMethodException => None
    }
  }

  private final lazy val skipInterfaces = {
    val mirror = scala.reflect.runtime.currentMirror
    Symbols.SuperTypesOfDef.flatMap { sym =>
      Try[Class[_]](mirror.runtimeClass(sym.asClass)).toOption
    }
  }

  // Do we want to return iterator instead?
  private def getSuperMethod(m: Method): Option[Method] = {
    val c = m.getDeclaringClass
    val superClass = c.getSuperclass
    val optClassMethod =
      if (superClass == null || skipInterfaces.contains(superClass))
        None
      else
        Try(superClass.getMethod(m.getName, m.getParameterTypes: _*)).toOption
    optClassMethod.orElse {
      val is = c.getInterfaces
      val methods = is.toIterator
        .filterNot(i => skipInterfaces.contains(i))
        .map(i => Try(i.getMethod(m.getName, m.getParameterTypes: _*)))
      val optInterfaceMethod = methods.collectFirst { case Success(m) => m }
      optInterfaceMethod
    }
  }

  // FIXME this is a hack, this should be handled in Passes
  // The problem is that rewriting in ProgramGraph.transform is non-recursive
  // We need some way to make isInvokeEnabled local to graph
  type InvokeTester = (Def[_], Method) => Boolean

  // we need to always invoke these for creating default values
  case class NamedUnpackTester(name: String, tester: UnpackTester) extends UnpackTester {
    def apply(e: Elem[_]) = tester(e)
  }

  case class NamedInvokeTester(name: String, tester: InvokeTester) extends InvokeTester {
    def apply(d: Def[_], m: Method) = tester(d, m)
  }

  private val isCompanionApply: InvokeTester = NamedInvokeTester("isCompanionApply",
    (_, m) => m.getName == "apply" && m.getDeclaringClass.getName.endsWith("CompanionCtor")
  )

  private lazy val isFieldGetterCache = collection.mutable.Map.empty[(Type, Method), Boolean]

  private val isFieldGetter: InvokeTester = NamedInvokeTester("isFieldGetter", { (d, m) =>
    val tpe = d.selfType.tag.tpe
    isFieldGetterCache.getOrElseUpdate((tpe, m),
      findScalaMethod(tpe, m).isParamAccessor)
  })

  protected def initialInvokeTesters: ArrayBuffer[InvokeTester] = {
    val res = new ArrayBuffer[InvokeTester](16)
    res += isCompanionApply
    res += isFieldGetter
    res
  }
  private lazy val invokeTesters: ArrayBuffer[InvokeTester] = initialInvokeTesters

  protected def invokeAll = true

  def isInvokeEnabled(d: Def[_], m: Method) = invokeAll || {
    if (_currentPass != null) {
      _currentPass.isInvokeEnabled(d, m).getOrElse {
        invokeTesters.exists(_(d, m))
      }
    }
    else
      invokeTesters.exists(_(d, m))
  }

  protected def shouldInvoke(d: Def[_], m: Method, args: Array[AnyRef]) = {
    m.getDeclaringClass.isAssignableFrom(d.getClass) && {
      isInvokeEnabled(d, m) ||
      // If method arguments include Scala functions, the method can't be staged directly.
      // In most cases it just stages the functions and calls a method which _can_ be staged.
      hasFuncArg(args) || {
        // Methods can only be staged if they return Rep[_]. For such methods
        // the JVM return type is Object if the method is defined in abstract context
        // and Exp if defined in staged context.
        // If neither holds, the method again should be invoked immediately.
        val returnClass = m.getReturnType
        !(returnClass == classOf[AnyRef] || returnClass == classOf[Sym])
      }
    }
  }

  def addInvokeTester(pred: InvokeTester): Unit = {
    invokeTesters += pred
  }

  def removeInvokeTester(pred: InvokeTester): Unit = {
    invokeTesters -= pred
  }

  def resetTesters() = {
    invokeTesters.clear()
    invokeTesters ++= initialInvokeTesters
    unpackTesters = initialUnpackTesters
  }

  protected def hasFuncArg(args: Array[AnyRef]): Boolean =
    args.exists {
      case f: Function0[_] => true
      case f: Function1[_, _] => true
      case f: Function2[_, _, _] => true
      case _ => false
    }

  // stack of receivers for which MethodCall nodes should be created by InvocationHandler
  protected var methodCallReceivers: List[Sym] = Nil

  import Symbols._

  def elemFromType(tpe: Type, elemMap: Map[Symbol, TypeDesc], baseType: Type): Elem[_] = tpe.dealias match {
    case TypeRef(_, classSymbol, params) => classSymbol match {
      case UnitSym => UnitElement
      case BooleanSym => BooleanElement
      case ByteSym => ByteElement
      case ShortSym => ShortElement
      case IntSym => IntElement
      case LongSym => LongElement
      case FloatSym => FloatElement
      case DoubleSym => DoubleElement
      case StringSym => StringElement
      case PredefStringSym => StringElement
      case CharSym => CharElement
      case Tuple2Sym =>
        val eA = elemFromType(params(0), elemMap, baseType)
        val eB = elemFromType(params(1), elemMap, baseType)
        pairElement(eA, eB)
      case EitherSym =>
        val eA = elemFromType(params(0), elemMap, baseType)
        val eB = elemFromType(params(1), elemMap, baseType)
        sumElement(eA, eB)
      case Function1Sym =>
        val eA = elemFromType(params(0), elemMap, baseType)
        val eB = elemFromType(params(1), elemMap, baseType)
        funcElement(eA, eB)
      case _ if classSymbol.asType.isParameter =>
        getDesc(elemMap, classSymbol, s"Can't create element for abstract type $tpe") match {
          case elem: Elem[_] => elem
          case cont: Cont[_] =>
            val paramElem = elemFromType(params(0), elemMap, baseType)
            cont.lift(paramElem)
        }
      case _ if classSymbol.isClass =>
        val paramDescs = params.zip(classSymbol.asType.toTypeConstructor.typeParams).map {
          case (typaram, formalParam) if typaram.takesTypeArgs =>
            // handle high-kind argument
            typaram match {
              case TypeRef(_, classSymbol, _) =>
                val desc = getDesc(elemMap, classSymbol, s"Can't find the descriptor for type argument $typaram of $tpe")
                desc match {
                  case cont: Cont[_] => cont
                  case _ =>
                    !!!(s"Expected a Cont, got $desc for type argument $typaram of $tpe")
                }
              case PolyType(_, _) =>
                // fake to make the compiler happy
                type F[A] = A

                new Cont[F] {
                  private val elemMap1: Map[Symbol, TypeDesc] = elemMap + (formalParam -> this)

                  def tag[T](implicit tT: WeakTypeTag[T]): WeakTypeTag[F[T]] = ???
                  def lift[T](implicit eT: Elem[T]): Elem[F[T]] = {
                    val tpe1 = appliedType(typaram, List(eT.tag.tpe))
                    // TODO incorrect baseType
                    elemFromType(tpe1, elemMap1, baseType).asInstanceOf[Elem[F[T]]]
                  }
                  def unlift[T](implicit eFT: Elem[F[T]]) = ???
                  def getElem[T](fa: Exp[F[T]]) = ???
                  def unapply[T](e: Elem[_]) = ???
                  override def getName(f: TypeDesc => String) = typaram.toString
                }
            }
          case (typaram, _) =>
            elemFromType(typaram, elemMap, baseType)
        }

        val descClasses = paramDescs.map {
          case e: Elem[_] =>
            classOf[Elem[_]]
          case c: Cont[_] =>
            // works due to type erasure; should be classOf[Cont[_]], but that's invalid syntax
            // See other uses of Cont[Any] here as well
            classOf[Cont[Any]]
          case d => !!!(s"Unknown type descriptior $d")
        }.toArray
        // entity type or base type
        // FIXME See https://github.com/scalan/scalan/issues/252
        if (classSymbol.asClass.isTrait || classSymbol == baseType.typeSymbol) {
          // abstract case, call *Element
          val entityName = classSymbol.name.toString
          val obj = getEntityObject(entityName).getOrElse(self)
          val methodName = StringUtil.lowerCaseFirst(entityName) + "Element"
          // self.getClass will return the final cake, which should contain the method
          try {
            val method = obj.getClass.getMethod(methodName, descClasses: _*)
            try {
              val resultElem = method.invoke(obj, paramDescs: _*)
              resultElem.asInstanceOf[Elem[_]]
            } catch {
              case e: Exception =>
                !!!(s"Failed to invoke $methodName($paramDescs)", e)
            }
          } catch {
            case _: NoSuchMethodException =>
              !!!(s"Failed to find element-creating method with name $methodName and parameter classes ${descClasses.map(_.getSimpleName).mkString(", ")}")
          }
        } else {
          val className = classSymbol.name.toString
          // concrete case, call viewElement(*Iso)
          val methodName = "iso" + className
          try {
            val entityObject = getEntityObject(className)
            val owner = entityObject.getOrElse(self)
            val method = owner.getClass.getMethod(methodName, descClasses: _*)
            try {
              val resultIso = method.invoke(owner, paramDescs: _*)
              resultIso.asInstanceOf[Iso[_, _]].eTo
            } catch {
              case e: Exception =>
                !!!(s"Failed to invoke $methodName($paramDescs)", e)
            }
          } catch {
            case e: Exception =>
              !!!(s"Failed to find iso-creating method with name $methodName and parameter classes ${descClasses.map(_.getSimpleName).mkString(", ")}")
          }
        }
    }
    case _ => !!!(s"Failed to create element from type $tpe")
  }

  private def getDesc(elemMap: Map[Symbol, TypeDesc], sym: Symbol, errorMessage: => String): TypeDesc =
    elemMap.get(sym).orElse {
      // For higher-kinded type parameters we may end with unequal symbols here
      elemMap.collectFirst {
        case (sym1, d) if sym.name == sym1.name && (internal.isFreeType(sym) || internal.isFreeType(sym1)) => d
      }
    }.getOrElse(!!!(errorMessage))

  private def extractParts(elem: Elem[_], classSymbol: Symbol, params: List[Type], tpe: Type): List[(TypeDesc, Type)] = classSymbol match {
    case UnitSym | BooleanSym | ByteSym | ShortSym | IntSym | LongSym |
         FloatSym | DoubleSym | StringSym | PredefStringSym | CharSym =>
      Nil
    case Tuple2Sym =>
      val elem1 = elem.asInstanceOf[PairElem[_, _]]
      List(elem1.eFst -> params(0), elem1.eSnd -> params(1))
    case EitherSym =>
      val elem1 = elem.asInstanceOf[SumElem[_, _]]
      List(elem1.eLeft -> params(0), elem1.eRight -> params(1))
    case Function1Sym =>
      val elem1 = elem.asInstanceOf[FuncElem[_, _]]
      List(elem1.eDom -> params(0), elem1.eRange -> params(1))
    case _ if classSymbol.isClass =>
      val declarations = classSymbol.asClass.selfType.decls
      val res = declarations.flatMap {
        case member =>
          val memberTpe = if (member.isMethod)
              member.asMethod.returnType.dealias
            else
              member.asTerm.typeSignature.dealias
          memberTpe match {
            // member returning Elem, such as eItem
            case TypeRef(_, ElementSym, params) =>
              val param = params(0).asSeenFrom(tpe, classSymbol)
              // There should be a method with the same name on the corresponding element class
              val elem1 = getParameterTypeDesc(elem, member.name.toString).asInstanceOf[Elem[_]]
              List(elem1 -> param)
            case TypeRef(_, ContSym, params) =>
              val param = params(0).asSeenFrom(tpe, classSymbol)
              // There should be a method with the same name on the corresponding element class
              val cont1 = getParameterTypeDesc(elem, member.name.toString).asInstanceOf[Cont[Any]]
              List(cont1 -> param)
            case _ => Nil
          }
      }.toList
      res
  }

  protected def getParameterTypeDesc(elem: Elem[_], paramName: String): AnyRef = {
    val correspondingElemMethod = elem.getClass.getMethod(paramName)
    val desc = correspondingElemMethod.invoke(elem)
    desc
  }

  @tailrec
  private def extractElems(elemsWithTypes: List[(TypeDesc, Type)],
                           unknownParams: Set[Symbol],
                           knownParams: Map[Symbol, TypeDesc]): Map[Symbol, TypeDesc] =
    if (unknownParams.isEmpty)
      knownParams
    else
      elemsWithTypes match {
        case Nil =>
          knownParams
        case (elem: Elem[_], tpe) :: rest =>
          tpe.dealias match {
            case TypeRef(_, classSymbol, params) => classSymbol match {
              case _ if classSymbol.asType.isParameter =>
                extractElems(rest, unknownParams - classSymbol, knownParams.updated(classSymbol, elem))
              case _ =>
                val elemParts = extractParts(elem, classSymbol, params, tpe)
                extractElems(elemParts ++ rest, unknownParams, knownParams)
            }
            case _ =>
              !!!(s"$tpe was not a TypeRef")
          }
        case (cont: Cont[_], tpe) :: rest =>
          val classSymbol = tpe.dealias match {
            case TypeRef(_, classSymbol, _) => classSymbol
            case PolyType(_, TypeRef(_, classSymbol, _)) => classSymbol
            case _ => !!!(s"Failed to extract symbol from $tpe")
          }

          if (classSymbol.asType.isParameter)
            extractElems(rest, unknownParams - classSymbol, knownParams.updated(classSymbol, cont))
          else {
//            val elem = cont.lift(UnitElement)
//            val elemParts = extractParts(elem, classSymbol, List(typeOf[Unit]), tpe)
            extractElems(/*elemParts ++ */rest, unknownParams, knownParams)
          }
        case (d, tpe) :: rest => !!!(s"Unknown TypeDesc $d")
      }

  // TODO Combine with extractElems
  private def getElemsMapFromInstanceElem(e: Elem[_], tpe: Type): Map[Symbol, TypeDesc] = {
    e match {
      case _: EntityElem[_] =>
        val kvs = tpe.dealias.typeSymbol.asType.typeParams.map {
          case sym =>
            val res = Try {
              if (sym.asType.toTypeConstructor.takesTypeArgs) {
                // FIXME hardcoding - naming convention is assumed to be consistent with ScalanCodegen
                val methodName = "c" + sym.name.toString
                val cont = invokeMethod(e, methodName).asInstanceOf[Cont[Any]]
                (cont, Map.empty[Symbol, TypeDesc])
              } else {
                val methodName = "e" + sym.name.toString
                val elem = invokeMethod(e, methodName).asInstanceOf[Elem[_]]
                val map1 = getElemsMapFromInstanceElem(elem, tpeFromElem(elem))
                (elem, map1)
              }
            }
            (sym, res)
        }
        val successes = kvs.collect { case (k, Success((desc, map))) => (k, desc, map) }
        val maps = successes.map(_._3)
        val map0 = successes.map { case (k, desc, _) => (k, desc) }.toMap
        maps.fold(map0)(_ ++ _)
      // The above lookup isn't necessary for other elements
      case _ => Map.empty
    }
  }

  private def invokeMethod(obj: AnyRef, methodName: String): AnyRef = {
    try {
      val method = obj.getClass.getMethod(methodName)
      try {
        val result = method.invoke(obj)
        result
      } catch {
        case e: Exception =>
          !!!(s"Failed to invoke $methodName of object $obj", e)
      }
    } catch {
      case _: NoSuchMethodException =>
        !!!(s"Failed to find method with name $methodName of object $obj")
    }
  }

  private def findScalaMethod(tpe: Type, m: Method) = {
    val scalaMethod0 = tpe.member(TermName(m.getName))
    if (scalaMethod0.isTerm) {
      val overloads = scalaMethod0.asTerm.alternatives
      val scalaMethod1 = (if (overloads.length == 1) {
        scalaMethod0
      } else {
        val javaOverloadId = ReflectionUtil.overloadId(m)

        overloads.find { sym =>
          !isSupertypeOfDef(sym.owner) && {
            val scalaOverloadId = ReflectionUtil.annotation[OverloadId](sym).map { sAnnotation =>
              val annotationArgs = sAnnotation.tree.children.tail
              val AssignOrNamedArg(_, Literal(Constant(sOverloadId))) = annotationArgs.head
              sOverloadId
            }
            scalaOverloadId == javaOverloadId
          }
        }.getOrElse {
          val overloadIdString = javaOverloadId.fold("no overload id")(x => s"""overload id "$x"""")
          !!!(s"Method with name ${m.getName} and $overloadIdString doesn't exist on type $tpe")
        }
      }).asMethod

      if (scalaMethod1.returnType.typeSymbol != definitions.NothingClass) {
        scalaMethod1
      } else {
        scalaMethod1.overrides.find(_.asMethod.returnType.typeSymbol != definitions.NothingClass).getOrElse {
          !!!(s"Method $scalaMethod1 on type $tpe and all overridden methods return Nothing")
        }.asMethod
      }
    } else
      !!!(s"Method $m couldn't be found on type $tpe")
  }

  def isStagedType(symName: String) =
    symName == "Rep" || symName == "Exp"

  protected def getMethodReturnTypeFromElem(e: Elem[_], m: Method): Type = {
    val tpe = tpeFromElem(e)
    val scalaMethod = findScalaMethod(tpe, m)
    // http://stackoverflow.com/questions/29256896/get-precise-return-type-from-a-typetag-and-a-method
    val returnType = scalaMethod.returnType.asSeenFrom(tpe, scalaMethod.owner).dealias
    returnType
  }

  protected def getResultElem(receiver: Sym, m: Method, args: List[AnyRef]): Elem[_] = {
    val e = receiver.elem
    val tpe = tpeFromElem(e)
    val instanceElemMap = getElemsMapFromInstanceElem(e, tpe)
    val scalaMethod = findScalaMethod(tpe, m)

    // http://stackoverflow.com/questions/29256896/get-precise-return-type-from-a-typetag-and-a-method
    val returnType = scalaMethod.returnType.asSeenFrom(tpe, scalaMethod.owner).dealias
    returnType match {
      // FIXME can't figure out proper comparison with RepType here
      case TypeRef(_, sym, List(tpe1)) if isStagedType(sym.name.toString) =>
        val paramTypes = scalaMethod.paramLists.flatten.map(_.typeSignature.asSeenFrom(tpe, scalaMethod.owner).dealias)
        // reverse to let implicit elem parameters be first
        val elemsWithTypes: List[(TypeDesc, Type)] = args.zip(paramTypes).reverse.flatMap {
          case (e: Sym, TypeRef(_, sym, List(tpeE))) if isStagedType(sym.name.toString) =>
            List(e.elem -> tpeE)
          case (elem: Elem[_], TypeRef(_, ElementSym, List(tpeElem))) =>
            List(elem -> tpeElem)
          case (cont: Cont[_], TypeRef(_, ContSym, List(tpeCont))) =>
            List(cont -> tpeCont)
          // below cases can be safely skipped without doing reflection
          case (_: Function0[_] | _: Function1[_, _] | _: Function2[_, _, _] | _: Numeric[_] | _: Ordering[_], _) => Nil
          case (obj, tpeObj) =>
            tpeObj.members.flatMap {
              case method: MethodSymbol if method.paramLists.isEmpty =>
                method.returnType.dealias match {
                  case TypeRef(_, ElementSym | ContSym, List(tpeElemOrCont)) =>
                    // TODO this doesn't work in InteractAuthExamplesTests.crossDomainStaged
                    // due to an apparent bug in scala-reflect. Test again after updating to 2.11

                    // val objMirror = runtimeMirror.reflect(obj)
                    // val methodMirror = objMirror.reflectMethod(method)
                    val jMethod = ReflectionUtil.methodToJava(method)
                    jMethod.invoke(obj) /* methodMirror.apply() */ match {
                      case elem: Elem[_] =>
                        List(elem -> tpeElemOrCont.asSeenFrom(tpeObj, method.owner))
                      case cont: Cont[_] =>
                        List(cont -> tpeElemOrCont.asSeenFrom(tpeObj, method.owner))
                      case x =>
                        !!!(s"$tpeObj.$method must return Elem or Cont but returned $x")
                    }
                  case _ =>
                    Nil
                }
              case _ => Nil
            }
        }

        try {
          val paramElemMap = extractElems(elemsWithTypes, scalaMethod.typeParams.toSet, Map.empty)
          val elemMap = instanceElemMap ++ paramElemMap
          elemFromType(tpe1, elemMap, definitions.NothingTpe)
        } catch {
          case e: Exception =>
            !!!(s"Failure to get result elem for method $m on type $tpe\nReturn type: $returnType", e)
        }
      case _ =>
        !!!(s"Return type of method $m should be a Rep, but is $returnType")
    }
  }

  private def tpeFromElem(e: Elem[_]): Type = {
    e.tag.tpe
  }

  private object Symbols {
    val RepSym = typeOf[Rep[_]].typeSymbol

    val UnitSym = typeOf[Unit].typeSymbol
    val BooleanSym = typeOf[Boolean].typeSymbol
    val ByteSym = typeOf[Byte].typeSymbol
    val ShortSym = typeOf[Short].typeSymbol
    val IntSym = typeOf[Int].typeSymbol
    val LongSym = typeOf[Long].typeSymbol
    val FloatSym = typeOf[Float].typeSymbol
    val DoubleSym = typeOf[Double].typeSymbol
    val StringSym = typeOf[String].typeSymbol
    val PredefStringSym = definitions.PredefModule.moduleClass.asType.toType.member(TypeName("String"))
    val CharSym = typeOf[Char].typeSymbol

    val Tuple2Sym = typeOf[(_, _)].typeSymbol
    val EitherSym = typeOf[_ | _].typeSymbol
    val Function1Sym = typeOf[_ => _].typeSymbol

    val ElementSym = typeOf[Elem[_]].typeSymbol
    val ContSym = typeOf[Cont[Any]].typeSymbol

    val SuperTypesOfDef = typeOf[Def[_]].baseClasses.toSet
  }

  private def isSupertypeOfDef(clazz: Symbol) =
    Symbols.SuperTypesOfDef.contains(clazz)

  lazy val externalClassNameMetaKey = MetaKey[String]("externalClassName")
  lazy val externalMethodNameMetaKey = MetaKey[String]("externalMethodName")

  sealed trait InvokeResult

  case class InvokeSuccess(result: Rep[_]) extends InvokeResult
  case class InvokeFailure(exception: Throwable) extends InvokeResult
  case object InvokeImpossible extends InvokeResult

  class ExpInvocationHandler[T](receiver: Exp[T]) extends InvocationHandler {
    override def toString = s"ExpInvocationHandler(${receiver.toStringWithDefinition})"

    def invoke(proxy: AnyRef, m: Method, _args: Array[AnyRef]) = {
      val args = if (_args == null) Array.empty[AnyRef] else _args

      def mkMethodCall(neverInvoke: Boolean) =
        Proxy.this.mkMethodCall(receiver, m, args.toList, neverInvoke)

      val res = findInvokableMethod(receiver, m, args)(identity)({
        case ExternalMethodException(className, methodName) =>
          mkMethodCall(neverInvoke = true)
            .setMetadata(externalClassNameMetaKey)(className)
            .setMetadata(externalMethodNameMetaKey)(methodName)
        case _: DelayInvokeException =>
          mkMethodCall(neverInvoke = false)
        case cause =>
          throwInvocationException("Method invocation", cause, receiver, m, args)
      })({
        mkMethodCall(neverInvoke = false)
      })
      res
    }
  }

  def throwInvocationException(whatFailed: String, cause: Throwable, receiver: Sym, m: Method, args: Seq[Any]) = {
    val deps = receiver +: args.flatMap(syms)
    !!!(s"$whatFailed (${receiver.toStringWithType}).${m.getName}(${args.mkString(", ")}) failed", baseCause(cause), deps: _*)
  }
}

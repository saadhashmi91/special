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
import debox.{Buffer => DBuffer}
import spire.syntax.all.cfor

import scala.collection.mutable.ArrayBuffer

trait MethodCalls extends Base with GraphVizExport { self: Scalan =>

  def delayInvoke = throw new DelayInvokeException

  /** Graph node to represent invocation of the method of some class.
    * @param receiver    node ref representing instance on which the method is called
    * @param method      method which is called (descriptor from `java.lang.reflect`)
    * @param args        node refs representing arguments passed to the method
    * @param neverInvoke it true this method cannot be performed, even if the
    *                    receiver node allow this
    * @param resultType  type descriptor of the method's result
    * @param isAdapterCall whether this MC was created by generated adapter class.
    *                      This typically means, that receiver node doesn't implement
    *                      given `method`.
    */
  case class MethodCall private[MethodCalls](receiver: Sym, method: Method, args: Seq[AnyRef], neverInvoke: Boolean)
                                            (val resultType: Elem[Any], val isAdapterCall: Boolean = false) extends Def[Any] {

    override def mirror(t: Transformer): Ref[Any] = {
      val len = args.length
      val args1 = new Array[AnyRef](len)
      cfor(0)(_ < len, _ + 1) { i =>
        args1(i) = transformProductParam(args(i), t).asInstanceOf[AnyRef]
      }
      val receiver1 = t(receiver)
      // in the case neverInvoke is false, the method is invoked in rewriteDef
      mkMethodCall(receiver1, method, args1, neverInvoke, isAdapterCall, resultType).asInstanceOf[Ref[Any]]
    }

    override def toString = {
      val methodStr = method.toString.replace("java.lang.", "").
        replace("public ", "").replace("abstract ", "")
      s"MethodCall($receiver, $methodStr, [${args.mkString(", ")}], $neverInvoke)"
    }

    /** Try invoke `method` on the node instance refered by `receiver`.
      * Each MC node contains enough information to perform invocation using
      * `java.lang.reflect.Method.invoke` method. However, this is not possible
      * if the node pointed to by `receiver` don't implement this method,
      * for example when receiver is Lambda variable pointing to Variable node
      * instance (in which case this MC was created by adapter)
      * @return invocation result descriptor.
      * @see InvokeResult
      */
    def tryInvoke: InvokeResult =
      if (neverInvoke && !isAdapterCall) {
        InvokeImpossible
      } else {
        invokeMethod[InvokeResult](
          receiver, method, args.toArray,
          { res => InvokeSuccess(res.asInstanceOf[Sym]) },
          { InvokeFailure(_) },
          { InvokeImpossible }
        )
      }

    import scalan.util.CollectionUtil.TraversableOps
    override def equals(other: Any): Boolean = (this eq other.asInstanceOf[AnyRef]) || {
      other match {
        case other: MethodCall =>
          receiver == other.receiver &&
          method == other.method &&
          resultType.name == other.resultType.name &&
          neverInvoke == other.neverInvoke &&
          isAdapterCall == other.isAdapterCall &&
          args.length == other.args.length &&
          args.sameElements2(other.args) // this is required in case method have T* arguments
        case _ => false
      }
    }

    override lazy val hashCode: Int = {
      var h = receiver.hashCode() * 31 + method.hashCode()
      h = h * 31 + resultType.name.hashCode
      h = h * 31 + (if(neverInvoke) 1 else 0)
      h = h * 31 + (if(isAdapterCall) 1 else 0)
      h = h * 31 + args.hashCode()
      h
    }
  }

  /** Represents invocation of constructor of the class described by `eA`.
    * @param  eA          class descriptor for new instance
    * @param  args        arguments of class constructor
    */
  case class NewObject[A](eA: Elem[A], args: Seq[Any]) extends BaseDef[A]()(eA) {
    override def transform(t: Transformer) = NewObject(eA, t(args))
  }

  /** Creates new MethodCall node and returns its node ref. */
  def mkMethodCall(receiver: Sym, method: Method, args: Seq[AnyRef],
                   neverInvoke: Boolean, isAdapterCall: Boolean, resultElem: Elem[_]): Sym = {
    reifyObject(MethodCall(receiver, method, args, neverInvoke)(asElem[Any](resultElem), isAdapterCall))
  }

  /** Creates new NewObject node and returns its node ref. */
  def newObjEx[A](args: Any*)(implicit eA: Elem[A]): Ref[A] = {
    reifyObject(NewObject[A](eA, args))
  }

  @tailrec
  private def baseCause(e: Throwable): Throwable = e match {
    case e: java.lang.reflect.UndeclaredThrowableException => baseCause(e.getCause)
    case e: net.sf.cglib.proxy.UndeclaredThrowableException => baseCause(e.getCause)
    case e: InvocationTargetException => baseCause(e.getCause)
    case e: ExceptionInInitializerError => baseCause(e.getCause)
    case e => e
  }

  /** Used by Graphviz dot file generator to format text label of the graph node. */
  override protected def formatDef(d: Def[_])(implicit config: GraphVizConfig): String = d match {
    case MethodCall(obj, method, args, _) =>
      val methodCallStr =
        s"${ScalaNameUtil.cleanScalaName(method.getName)}(${args.mkString(", ")})"
      if (obj.isCompanionType) {
        s"$obj.$methodCallStr"
      } else {
        val className = ScalaNameUtil.cleanNestedClassName(method.getDeclaringClass.getName)
        s"$obj.$className.$methodCallStr"
      }
    case NewObject(eA, args) =>
      val className = ScalaNameUtil.cleanNestedClassName(eA.sourceType.name)
      s"new $className(${args.mkString(", ")})"
    case _ => super.formatDef(d)
  }

  /** This method is called for each MethodCall node which is about to be added to the graph.
    * This means `mc` has been examined by all the rewrite rules, but has not need rewritten.
    * Now, if this method returns null, then mc will be added to the graph.
    * However, in this method, `mc` can be examined by a second set of RW rules
    * (kind of lower priority rules). These rules kind of context dependent, because at this
    * point we know that the first RW set didn't triggered any rewrite. */
  def rewriteNonInvokableMethodCall(mc: MethodCall): Ref[_] = null

  /** Create delegate instance suitable for method invocation.
    * It is used when T is a class or a trait and the node refered by x doesn't conform to T.
    * This method returns dynamically constructed instance, which conforms to T.
    * Whenever a method of T is called on that instance, the call is intercepted and
    * `DelegatedInterceptionHandler.invoke` method is called, then a new MethodCall can
    * be constructed.
    */
  def unrefDelegate[T <: AnyRef](x: Ref[T])(implicit ct: ClassTag[T]): T = {
    val d = x.node
    if (d.isInstanceOf[Const[_]])
      d.asInstanceOf[Const[T]@unchecked].x
    else
      getDelegate(x, ct)
  }

  /** Used to cache generated delegate classes along with instantiated instances of the class.*/
  case class CachedDelegateClass(delegateClass: Class[_ <: AnyRef], instances: AVHashMap[Sym, AnyRef])

  private lazy val delegateCache = AVHashMap[ClassTag[_], CachedDelegateClass](100)
  private lazy val objenesis = new ObjenesisStd

  /** Construct delegate instance for the given type and receiver object. */
  private def getDelegate[T](x: Ref[T], ct: ClassTag[T]): T = {
    val cachedOpt = delegateCache.get(ct)
    val entry = if (cachedOpt.isEmpty) {
      val clazz = ct.runtimeClass
      val e = new Enhancer
      e.setClassLoader(clazz.getClassLoader)
      e.setSuperclass(clazz)
      e.setCallbackType(classOf[DelegatedInvocationHandler[_]])
      val delegateClass = e.createClass().asSubclass(classOf[AnyRef])
      val entry = CachedDelegateClass(delegateClass, AVHashMap[Sym, AnyRef](100))
      delegateCache.put(ct, entry)
      entry
    } else
      cachedOpt.get

    val delegateOpt = entry.instances.get(x)
    if (delegateOpt.isEmpty) {
      val delegateInstance = objenesis.newInstance(entry.delegateClass).asInstanceOf[Factory]
      delegateInstance.setCallback(0, new DelegatedInvocationHandler(x))
      entry.instances.put(x, delegateInstance)
      delegateInstance.asInstanceOf[T]
    }
    else
      delegateOpt.get.asInstanceOf[T]
  }

  /** Generic helper to call the given method on the given receiver node. */
  private def invokeMethod[A](receiver: Sym, m: Method, args: Array[AnyRef],
                              onInvokeSuccess: AnyRef => A,
                              onInvokeException: Throwable => A,
                              onInvokeImpossible: => A): A = {
    val d = receiver.node
    if (canBeInvoked(d, m, args)) {
      try {
        val res = m.invoke(d, args: _*)
        onInvokeSuccess(res)
      } catch {
        case e: Exception => onInvokeException(baseCause(e))
      }
    }
    else
      onInvokeImpossible
  }

  /** Method invocation enabler.
    * @return  true if the given method can be invoked on the given node. */
  def isInvokeEnabled(d: Def[_], m: Method) = true

  /** Method invocation checker. */
  protected def canBeInvoked(d: Def[_], m: Method, args: Array[AnyRef]) = {
    m.getDeclaringClass.isAssignableFrom(d.getClass) && isInvokeEnabled(d, m)
  }

  sealed trait InvokeResult

  case class InvokeSuccess(result: Ref[_]) extends InvokeResult
  case class InvokeFailure(exception: Throwable) extends InvokeResult
  case object InvokeImpossible extends InvokeResult

  /** Handles intercepted invocations of method on delegates. */
  class DelegatedInvocationHandler[T](receiver: Ref[T]) extends InvocationHandler {
    override def toString = s"ExpInvocationHandler(${receiver.toStringWithDefinition})"

    def invoke(delegate: AnyRef, m: Method, _args: Array[AnyRef]) = {
      val args = if (_args == null) Array.empty[AnyRef] else _args

      val res = invokeMethod(receiver, m, args, identity, {
        case cause =>
          throwInvocationException("Method invocation", cause, receiver, m, args)
      }, {
        !!!(s"Invocation handler is only supported for successful pass: ExpInvocationHandler($receiver).invoke($delegate, $m, ${args.toSeq})")
      })
      res
    }
  }

  def throwInvocationException(whatFailed: String, cause: Throwable, receiver: Sym, m: Method, args: Seq[Any]) = {
    val buf = DBuffer.empty[Sym]
    buf += receiver
    Def.extractSyms(args, buf)
    val deps = buf.toArray()
    !!!(s"$whatFailed (${receiver.varNameWithType}).${m.getName}(${args.mkString(", ")}) failed", baseCause(cause), deps: _*)
  }
}

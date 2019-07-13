package scalan.staged

import java.lang.reflect.Method

import scala.collection.{Seq, mutable}
import scalan.{Lazy, DelayInvokeException, Scalan, Nullable, ScalanEx}
import scala.reflect.runtime.universe._

trait Transforming { self: Scalan =>

  trait Pass {
    def name: String
    def config: PassConfig = Pass.defaultPassConfig
    def doFinalization(): Unit = {}
    /**
      * Pass specific optional decision.
      * @param d receiver of the method
      * @param m method to invoke
      * @return Some(decision) if some this Pass defines some logic, None - then core behavior is used
      */
    def isInvokeEnabled(d: Def[_], m: Method): Option[Boolean] = None
  }
  object Pass {
    val defaultPassName = "default"
    val defaultPass = new DefaultPass(defaultPassName)
    val defaultPassConfig = defaultPass.config
  }

  case class PassConfig(
                         shouldUnpackTuples: Boolean = false,
                         shouldExtractFields: Boolean = true,
                         constantPropagation: Boolean = true,
                         shouldSlice: Boolean = false
                       )
  {
    def withConstantPropagation(value: Boolean) = this.copy(constantPropagation = value)
  }

  class DefaultPass(val name: String, override val config: PassConfig = PassConfig()) extends Pass

  //TODO optimize: parallel execution of Compilers
  // Current design doesn't allow to run through passes i two Compilers in parallel
  var _currentPass: Pass = Pass.defaultPass
  def currentPass = _currentPass

  def beginPass(pass: Pass): Unit = {
    _currentPass = pass
  }
  def endPass(pass: Pass): Unit = {
    _currentPass = Pass.defaultPass
  }

  case class SingletonElem[T: WeakTypeTag](value: T) extends BaseElem[T](value)

  sealed trait KeyPath {
    def isNone = this == KeyPath.None
    def isAll = this == KeyPath.All
  }
  object KeyPath {
    case object Root extends KeyPath
    case object This extends KeyPath
    case object All extends KeyPath
    case object None extends KeyPath
    case object First extends KeyPath
    case object Second extends KeyPath
    case class Field(name: String) extends KeyPath
  }

  def keyPathElem(kp: KeyPath): Elem[KeyPath] = SingletonElem(kp)

  implicit class KeyPathElemOps(eKeyPath: Elem[KeyPath]) {
    def keyPath = eKeyPath.asInstanceOf[SingletonElem[KeyPath]].value
  }

  class MapTransformer(private val subst: Map[Sym, Sym]) extends Transformer {
    def this(substPairs: (Sym, Sym)*) {
      this(substPairs.toMap)
    }
    def apply[A](x: Rep[A]): Rep[A] = subst.get(x) match {
      case Some(y) if y != x => apply(y.asInstanceOf[Rep[A]]) // transitive closure
      case _ => x
    }
    def isDefinedAt(x: Rep[_]) = subst.contains(x)
    def domain: Set[Rep[_]] = subst.keySet

    override def toString = if (subst.isEmpty) "MapTransformer.Empty" else s"MapTransformer($subst)"
  }

  object MapTransformer {
    val Empty = new MapTransformer(Map.empty[Sym, Sym])

    implicit val ops: TransformerOps[MapTransformer] = new TransformerOps[MapTransformer] {
      def empty = Empty//new MapTransformer(Map.empty)
      def add[A](t: MapTransformer, kv: (Rep[A], Rep[A])): MapTransformer =
        new MapTransformer(t.subst + kv)
    }
  }

  implicit class PartialRewriter(pf: PartialFunction[Sym, Sym]) extends Rewriter {
    def apply[T](x: Rep[T]): Rep[T] =
      if (pf.isDefinedAt(x))
        pf(x).asInstanceOf[Rep[T]]
      else
        x
  }

  object DecomposeRewriter extends Rewriter {
    def apply[T](x: Rep[T]): Rep[T] = x match {
      case Def(d) => decompose(d) match {
        case None => x
        case Some(y) => y
      }
      case _ => x
    }
  }

  object InvokeRewriter extends Rewriter {
    def apply[T](x: Rep[T]): Rep[T] = x match {
      case Def(call: MethodCall) =>
        call.tryInvoke match {
          case InvokeSuccess(res) =>
            res.asInstanceOf[Rep[T]]
          case InvokeFailure(e) =>
            if (e.isInstanceOf[DelayInvokeException])
              x
            else
              !!!(s"Failed to invoke $call", e, x)
          case _ => x
        }
      case _ => x
    }
  }

  abstract class Rewriter { self =>
    def apply[T](x: Rep[T]): Rep[T]

    def orElse(other: Rewriter): Rewriter = new Rewriter {
      def apply[T](x: Rep[T]) = {
        val y = self(x)
        (x == y) match { case true => other(x) case _ => y }
      }
    }
    def andThen(other: Rewriter): Rewriter = new Rewriter {
      def apply[T](x: Rep[T]) = {
        val y = self(x)
        val res = other(y)
        res
      }
    }

    def |(other: Rewriter) = orElse(other)
    def ~(other: Rewriter) = andThen(other)
  }

  val NoRewriting: Rewriter = new Rewriter {
    def apply[T](x: Rep[T]) = x
  }

  abstract class Mirror[Ctx <: Transformer : TransformerOps] {
    def apply[A](t: Ctx, rewriter: Rewriter, node: Rep[A], d: Def[A]): (Ctx, Sym) = (t, transformDef(d, t))

    protected def mirrorElem(node: Sym): Elem[_] = node.elem

    // every mirrorXXX method should return a pair (t + (v -> v1), v1)
    protected def mirrorVar[A](t: Ctx, rewriter: Rewriter, v: Rep[A]): (Ctx, Sym) = {
      val newVar = variable(Lazy(mirrorElem(v)))
      (t + (v -> newVar), newVar)
    }

    protected def mirrorDef[A](t: Ctx, rewriter: Rewriter, node: Rep[A], d: Def[A]): (Ctx, Sym) = {
      val (t1, res) = apply(t, rewriter, node, d)
      (t1 + (node -> res), res)
    }

    protected def getMirroredLambdaSym[A, B](node: Rep[A => B]): Sym = placeholder(Lazy(mirrorElem(node)))

    // require: should be called after oldlam.schedule is mirrored
    private def getMirroredLambdaDef(t: Ctx, oldLam: Lambda[_,_], newRoot: Sym): Lambda[_,_] = {
      val newVar = t(oldLam.x)
      val newLambdaDef = new Lambda(Nullable.None, newVar, newRoot, oldLam.mayInline, oldLam.alphaEquality)
      newLambdaDef
    }

    protected def mirrorLambda[A, B](t: Ctx, rewriter: Rewriter, node: Rep[A => B], lam: Lambda[A, B]): (Ctx, Sym) = {
      var tRes: Ctx = t
      val (t1, _) = mirrorNode(t, rewriter, lam, lam.x)

      // original root
      val originalRoot = lam.y

      // ySym will be assigned after f is executed
      val ySym = placeholder(Lazy(lam.y.elem))
      val newLambdaCandidate = getMirroredLambdaDef(t1, lam, ySym)
      val newLambdaSym = newLambdaCandidate.self

      // new effects may appear during body mirroring
      // thus we need to forget original Reify node and create a new one
      val oldStack = lambdaStack
      try {
        lambdaStack = newLambdaCandidate :: lambdaStack
        val newRoot = reifyEffects({
          val schedule = lam.scheduleSingleLevel
          val (t2, _) = mirrorSymbols(t1, rewriter, lam, schedule)
          tRes = t2
          tRes(originalRoot) // this will be a new root
        })
        ySym.assignDefFrom(newRoot)
      }
      finally {
        lambdaStack = oldStack
      }

      // we don't use toExp here to avoid rewriting pass for new Lambda
      val resLam = findOrCreateDefinition(newLambdaCandidate, newLambdaSym)

// TODO metadata is not processed (for performance, since we don't need it yet)
//      val (tRes2, mirroredMetadata) = mirrorMetadata(tRes, node, newLambdaExp)
//      val resLam = rewriteUntilFixPoint(newLambdaExp, mirroredMetadata, rewriter)

      (tRes + (node -> resLam), resLam)
    }

    protected def mirrorBranch[A](t: Ctx, rewriter: Rewriter, g: AstGraph, branch: ThunkDef[A]): (Ctx, Sym) = {
      // get original root unwrapping Reify nodes
      val originalRoot = branch.root
      val schedule = branch.scheduleSingleLevel
      val (t2, _) = mirrorSymbols(t, rewriter, branch, schedule)
      val newRoot = t2(originalRoot)
      (t2, newRoot)
    }

    protected def mirrorIfThenElse[A](t: Ctx, rewriter: Rewriter, g: AstGraph, node: Rep[A], ite: IfThenElse[A]): (Ctx, Sym) = {
      g.branches.ifBranches.get(node) match {
        case Some(branches) =>
          var tRes: Ctx = t
          val newIte = ifThenElse(t(ite.cond), {
            val (t1, res) = mirrorBranch(t, rewriter, g, branches.thenBody)
            tRes = t1
            res
          }, {
            val (t2, res) = mirrorBranch(tRes, rewriter, g, branches.elseBody)
            tRes = t2
            res
          })
          (tRes + (node -> newIte), newIte)
        case _ =>
          mirrorDef(t, rewriter, node, ite)
      }
    }

    protected def mirrorThunk[A](t: Ctx, rewriter: Rewriter, node: Rep[Thunk[A]], thunk: ThunkDef[A]): (Ctx, Sym) = {
      var schedulePH: Schedule = null
      val newRootPH = placeholder(Lazy(node.elem.eItem))
      val newThunk = new ThunkDef(newRootPH, { assert(schedulePH != null); schedulePH })
      val newThunkSym = newThunk.self

      val newScope = thunkStack.beginScope(newThunkSym)
      val schedule = thunk.scheduleSyms
      val (t1, newSchedule) = mirrorSymbols(t, rewriter, thunk, schedule)
      thunkStack.endScope()

      val newRoot = t1(thunk.root)
      newRootPH.assignDefFrom(newRoot)
      schedulePH =
          if (newRoot.isVar) Nil
          else if (newScope.isEmptyBody)  Nil
          else newScope.scheduleForResult(newRoot)

      createDefinition(newThunkSym, newThunk)
      (t1 + (node -> newThunkSym), newThunkSym)
    }

    protected def isMirrored(t: Ctx, node: Sym): Boolean = t.isDefinedAt(node)

    def mirrorNode[A](t: Ctx, rewriter: Rewriter, g: AstGraph, node: Rep[A]): (Ctx, Sym) = {
      if (isMirrored(t, node)) {
        (t, t(node))
      } else {
        node match {
          case Def(d) => d match {
            case v: Variable[_] =>
              mirrorVar(t, rewriter, node)
            case lam: Lambda[a, b] =>
              mirrorLambda(t, rewriter, node.asInstanceOf[Rep[a => b]], lam)
            case th: ThunkDef[a] =>
              mirrorThunk(t, rewriter, node.asInstanceOf[Rep[Thunk[a]]], th)
            case ite: IfThenElse[a] =>
              mirrorIfThenElse(t, rewriter, g, node.asInstanceOf[Rep[a]], ite)
            case _ =>
              mirrorDef(t, rewriter, node, d)
          }
        }
      }
    }

    /** @hotspot */
    def mirrorSymbols(t0: Ctx, rewriter: Rewriter, g: AstGraph, nodes: Seq[Int]) = {
      val buf = scala.collection.mutable.ArrayBuilder.make[Sym]()
      buf.sizeHint(nodes.length)
      val t = nodes.foldLeft(t0) {
        case (t1, n) =>
          val (t2, n1) = mirrorNode(t1, rewriter, g, getSym(n))
          buf += n1
          t2
      }
      (t, buf.result())
    }
  }

  def mirror[Ctx <: Transformer : TransformerOps] = new Mirror[Ctx] {}
  val DefaultMirror = mirror[MapTransformer]

}

trait TransformingEx { self: ScalanEx =>

  abstract class MirrorEx[Ctx <: Transformer : TransformerOps] extends Mirror[Ctx] {

    protected def mirrorMetadata[A, B](t: Ctx, old: Rep[A], mirrored: Rep[B]) =
      (t, allMetadataOf(old))

    private def setMirroredMetadata(t1: Ctx, node: Sym, mirrored: Sym): (Ctx, Sym) = {
      val (t2, mirroredMetadata) = mirrorMetadata(t1, node, mirrored)
      setAllMetadata(mirrored, mirroredMetadata.filterSinglePass)
      (t2, mirrored)
    }

  }

  //  sealed abstract class TupleStep(val name: String)
  //  case object GoLeft extends TupleStep("L")
  //  case object GoRight extends TupleStep("R")
  type TuplePath = List[Int]

  def projectPath(x:Rep[Any], path: TuplePath) = {
    val res = path.foldLeft(x)((y,i) => TupleProjection(y.asInstanceOf[Rep[(Any,Any)]], i))
    res
  }

  // build projection from the root taking projection structure from the tree
  // assert(result.root == root)
  // NOTE: tree.root is not used
  def projectTree(root:Rep[Any], tree: ProjectionTree): ProjectionTree = {
    val newChildren = tree.children.map(child => {
      val i = projectionIndex(child.root)
      val newChildRoot = TupleProjection(root.asInstanceOf[Rep[(Any,Any)]], i)
      projectTree(newChildRoot, child)
    })
    ProjectionTree(root, newChildren)
  }

  def pairMany(env: List[Sym]): Sym =
    env.reduceRight(Pair(_, _))

  abstract class SymbolTree {
    def root: Sym
    def children: List[SymbolTree]
    def mirror(leafSubst: Sym => Sym): SymbolTree
    def paths: List[(TuplePath, Sym)]
    def isLeaf = children.isEmpty
  }

  class ProjectionTree(val root: Sym, val children: List[ProjectionTree]) extends SymbolTree {
    override def toString = s"""ProjTree(\n${paths.mkString("\n")})"""

    lazy val paths: List[(TuplePath, Sym)] =
      if (isLeaf) List((Nil, root))
      else{
        for {
          ch <- children
          (p, s) <- ch.paths
        } yield {
          val i = projectionIndex(ch.root)
          (i :: p, s)
        }
      }

    def mkNewTree(r: Sym, cs: List[ProjectionTree]) = ProjectionTree(r, cs)
    def mirror(subst: Sym => Sym): ProjectionTree = {
      val newRoot = subst(root)
      projectTree(newRoot, this)
    }
  }
  object ProjectionTree {
    def apply(root: Sym, children: List[ProjectionTree]) = new ProjectionTree(root, children)
    def apply(root: Sym, unfoldChildren: Sym => List[Sym]): ProjectionTree =
      ProjectionTree(root, unfoldChildren(root) map (apply(_, unfoldChildren)))
  }

  class TupleTree(val root: Sym, val children: List[TupleTree]) extends SymbolTree {
    override def toString =
      if (isLeaf) root.toString
      else "Tup(%s)".format(children.mkString(","))

    lazy val paths: List[(TuplePath, Sym)] = children match {
      case Nil => List((Nil, root))
      case _ =>
        for {
          (i,ch) <- children.indices.toList zip children
          (p, s) <- ch.paths
        } yield (i + 1 :: p, s)
    }

    def mirror(leafSubst: Sym => Sym): TupleTree =
      if (isLeaf)
        TupleTree(leafSubst(root), Nil)
      else {
        val newChildren = children map (_.mirror(leafSubst))
        val newRoot = pairMany(newChildren map (_.root))
        TupleTree(newRoot, newChildren)
      }
  }

  object TupleTree {
    def apply(root: Sym, children: List[TupleTree]) = new TupleTree(root, children)

    // require ptree to be sorted by projectionIndex
    def fromProjectionTree(ptree: ProjectionTree, subst: Sym => Sym): TupleTree =
      if (ptree.isLeaf)
        TupleTree(subst(ptree.root), Nil)
      else {
        val newChildren = ptree.children map (fromProjectionTree(_, subst))
        val newRoot = pairMany(newChildren map (_.root))
        TupleTree(newRoot, newChildren)
      }

    def unapply[T](s: Rep[T]): Option[TupleTree] = {
      s match {
        case Def(Tup(TupleTree(l),TupleTree(r))) =>
          Some(TupleTree(s, List(l, r)))
        case _ => Some(TupleTree(s, Nil))
      }
    }
  }

  abstract class Analyzer {
    def name: String
    override def toString = s"Analysis($name)"
  }

  trait Lattice[M[_]] {
    def maximal[T:Elem]: Option[M[T]]
    def minimal[T:Elem]: Option[M[T]]
    def join[T](a: M[T], b: M[T]): M[T]
  }

  trait BackwardAnalyzer[M[_]] extends Analyzer {
    type MarkedSym = (Rep[T], M[T]) forSome {type T}
    type MarkedSyms = Seq[MarkedSym]
    def keyPrefix: String = name

    def lattice: Lattice[M]
    def defaultMarking[T:Elem]: M[T]

    def updateMark[T](s: Rep[T], other: M[T]): (Rep[T], M[T]) = {
      s -> lattice.join(getMark(s), other)
    }

    def beforeAnalyze[A,B](l: Lambda[A,B]): Unit = {}

    def getInboundMarkings[T](thisSym: Rep[T], outMark: M[T]): MarkedSyms

    def getLambdaMarking[A,B](lam: Lambda[A,B], mDom: M[A], mRange: M[B]): M[A => B]

    def getMarkingKey[T](implicit eT:Elem[T]): MetaKey[M[T]] = markingKey[T](keyPrefix).asInstanceOf[MetaKey[M[T]]]

    def clearMark[T](s: Rep[T]): Unit = {
      implicit val eT = s.elem
      s.removeMetadata(getMarkingKey[T])
    }

    def getMark[T](s: Rep[T]): M[T] = {
      implicit val eT = s.elem
      val mark = s.getMetadata(getMarkingKey[T]).getOrElse(defaultMarking[T])
      mark
    }

    def hasMark[T](s: Rep[T]): Boolean = {
      implicit val eT = s.elem
      s.getMetadata(getMarkingKey[T]).isDefined
    }

    def updateOutboundMarking[T](s: Rep[T], mark: M[T]): Unit = {
      implicit val eT = s.elem
      val current = getMark(s)
      val updated = lattice.join(current, mark)
      val key = getMarkingKey[T]
      s.setMetadata(key)(updated, Some(true))
    }

    def backwardAnalyzeRec(g: AstGraph): Unit = {
      val revSchedule = g.schedule.reverseIterator
      for (sym <- revSchedule) sym match { case s: Rep[t] =>
        val d = s.rhs
        // back-propagate analysis information (including from Lambda to Lambda.y, see LevelAnalyzer)
        val outMark = getMark(s)
        val inMarks = getInboundMarkings[t](s, outMark)
        for ((s, mark) <- inMarks) {
          updateOutboundMarking(s, mark)
        }
        d match {
          // additionally if it is Lambda
          case l: Lambda[a,b] =>
            // analyze lambda after the markings were assigned to the l.y during previous propagation step
            backwardAnalyzeRec(l)
            // markings were propagated up to the lambda variable
            val mDom = getMark(l.x)
            val mRange = getMark(l.y)

            // update markings attached to l
            val lMark = getLambdaMarking(l, mDom, mRange)
            updateOutboundMarking(l.self, lMark)
          case _ =>
        }
      }
    }
  }

  trait Marking[T] {
    def elem: Elem[T]
    def basePath: KeyPath = KeyPath.Root
    def nonEmpty: Boolean
  }

  class EmptyMarking[T](val elem: Elem[T]) extends Marking[T] {
    def nonEmpty = false
  }

  type MarkedSym = (Rep[T], Marking[T]) forSome {type T}
  type MarkedSyms = Seq[MarkedSym]

  class MarkingElem[T:Elem] extends BaseElem[Marking[T]](new EmptyMarking[T](element[T]))
  implicit def markingElem[T:Elem] = new MarkingElem[T]

  private val markingKeys = mutable.Map.empty[(String, Elem[_]), MetaKey[_]]

  def markingKey[T](prefix: String)(implicit eT:Elem[T]): MetaKey[Marking[T]] = {
    val key = markingKeys.getOrElseUpdate((prefix, eT), MetaKey[Marking[T]](s"${prefix}_marking[${eT.name}]"))
    key.asInstanceOf[MetaKey[Marking[T]]]
  }
}
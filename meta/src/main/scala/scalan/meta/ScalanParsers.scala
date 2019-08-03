/**
 * User: Alexander Slesarenko
 * Date: 11/17/13
 */
package scalan.meta

import java.io.File

import scala.language.implicitConversions
import scala.tools.nsc.Global
import scala.reflect.internal.util.{BatchSourceFile, SourceFile, OffsetPosition, RangePosition}
import scalan.meta.ScalanAst._
import java.util.regex.Pattern

import scalan.meta.Symbols._
import scalan.util.StringUtil._
import scalan.util.CollectionUtil._
import scalan.util.FileUtil

trait ScalanParsers[+G <: Global] {
  def getGlobal: G
  lazy val global: G = getGlobal

  type Compiler = global.type
  lazy val compiler: Compiler = global
  import compiler._

  val context: AstContext

  class ParseCtx(val isVirtualized: Boolean)(implicit val astContext: AstContext) {
    def onParsed(scalaSym: SymTreeApi, sdef: NamedDef) = {}
  }

  implicit def parseCtxToAstContext(implicit ctx: ParseCtx) = ctx.astContext
  
  implicit def nameToString(name: Name): String = name.toString

  implicit class OptionListOps[A](opt: Option[List[A]]) {
    def flatList: List[A] = opt.toList.flatten
  }

  implicit class MemberDefOps[A](tree: MemberDef) {
    def collectAnnotations = {
      // SI-5885: by default this won't return annotations of not yet initialized symbols
      val annots = tree.symbol.annotations.map(_.tree) match {
        case Nil  => tree.mods.annotations
        case anns => anns
      }
      annots
    }
  }

  private def positionString(tree: Tree) = {
    tree.pos match {
      case pos: RangePosition =>
        val path = pos.source.file.canonicalPath
        s"file $path at ${pos.line}:${pos.column} (start ${pos.point - pos.start} before, end ${pos.end - pos.point} after)"
      case pos: OffsetPosition =>
        val path = pos.source.file.canonicalPath
        s"file $path at ${pos.line}:${pos.column}"
      case pos => pos.toString
    }
  }

  def !!!(msg: String, tree: Tree) = {
    val fullMsg = s"$msg at ${positionString(tree)}"
    throw new IllegalStateException(fullMsg)
  }

  def !!!(msg: String) = Base.!!!(msg)

  def ???(tree: Tree) = {
    val pos = tree.pos
    val msg = s"Unhandled case in ${positionString(tree)}:\nAST: ${showRaw(tree)}\n\nCode for AST: $tree"
    throw new IllegalStateException(msg)
  }

  def inform(msg: String) = global.inform(msg)

  def parseUnitFile(file: File)(implicit ctx: ParseCtx) = {
    val sourceFile = compiler.getSourceFile(file.getPath)
    val tree = parseFile(sourceFile)
    unitDefFromTree(file.getPath, tree)
  }

  def parseFile(source: SourceFile): compiler.Tree = {
    compiler.newUnitParser(new compiler.CompilationUnit(source)).parse()
  }

  sealed trait TreeKind
  case object TopLevel extends TreeKind
  case object Type extends TreeKind
  case object Member extends TreeKind
  case object Expr extends TreeKind
  case object Annotation extends TreeKind
  case object AnnotationArg extends TreeKind

  def parseString(kind: TreeKind, prog: String): Tree = {
    // wrap the string into a complete file
    val prog1 = kind match {
      case TopLevel => prog
      case Type => s"object o { val x: $prog }"
      case Member => s"object o { $prog }"
      case Expr => s"object o { val x = $prog }"
      case Annotation => s"object o { @$prog val x = null }"
      case AnnotationArg => s"object o { @OverloadId($prog) val x = null }"
    }
    val fakeSourceFile = new BatchSourceFile("<no file>", prog1.toCharArray)
    // extract the part corresponding to original prog
    (kind, parseFile(fakeSourceFile)) match {
      case (TopLevel, tree) => tree
      case (Member, PackageDef(_, List(ModuleDef(_, _, Template(_, _, List(_, tree)))))) =>
        tree
      case (Type, PackageDef(_, List(ModuleDef(_, _, Template(_, _, List(_, ValDef(_, _, tree, _))))))) =>
        tree
      case (Expr, PackageDef(_, List(ModuleDef(_, _, Template(_, _, List(_, ValDef(_, _, _, tree))))))) =>
        tree
      case (Annotation, PackageDef(_, List(ModuleDef(_, _, Template(_, _, List(_, ValDef(Modifiers(_,_,List(tree)), _, _, _))))))) =>
        tree
      case (AnnotationArg, PackageDef(_, List(ModuleDef(_, _, Template(_, _, List(_, ValDef(Modifiers(_,_,List(ExtractAnnotation(_,_,List(tree)))), _, _, _))))))) =>
        tree
      case (kind, tree) =>
        ???(tree)
    }
  }

  def parseType(owner: SSymbol, tpeString: String)(implicit ctx: ParseCtx): STpeExpr = {
    val tree = parseString(Type, tpeString)
    val tpe = tpeExpr(owner, tree)
    tpe
  }

  def parseExpr(owner: SSymbol, exprString: String)(implicit ctx: ParseCtx): SExpr = {
    val tree = parseString(Expr, exprString)
    val expr = parseExpr(owner, tree)
    expr
  }

  def parseBodyItem(owner: SSymbol, defString: String)(implicit ctx: ParseCtx): SBodyItem = {
    val tree = parseString(Member, defString)
    val res = optBodyItem(owner, tree, None).get
    res
  }

  def parseMethod(owner: SSymbol, mdString: String)(implicit ctx: ParseCtx): SMethodDef = {
    parseBodyItem(owner, mdString).asInstanceOf[SMethodDef]
  }

  def unitDefFromTree(file: String, tree: Tree)(implicit ctx: ParseCtx): SUnitDef = tree match {
    case pd: PackageDef =>
      val unitName = scala.reflect.io.File(file).stripExtension
      unitDefFromPackageDef(unitName, pd)
    case tree =>
      throw new Exception(s"Unexpected tree in $file: expected PackageDef but was \n\n$tree")
  }

  def loadUnitDefFromResource(fileName: String)(implicit ctx: ParseCtx): SUnitDef = {
    try {
      val sourceCode = FileUtil.readAndCloseStream(this.getClass.getClassLoader.getResourceAsStream(fileName))
      val sourceFile = new BatchSourceFile(fileName, sourceCode)
      val tree = parseFile(sourceFile)
      val module = unitDefFromTree(fileName, tree)
      module
    } catch {
      case t: Throwable => throw new IllegalStateException(s"Cannot load SUnitDef from $fileName", t)
    }
  }

  def findClassDefByName(trees: List[Tree], name: String): Option[ClassDef] =
    trees.collectFirst {
      case cd: ClassDef if cd.name.toString == name => cd
    }

  def isInternalMethodOfCompanion(md: SMethodDef, outerScope: List[SBodyItem]): Boolean = {
    val moduleVarName = md.name + global.nme.MODULE_VAR_SUFFIX.toString
    val hasClass = outerScope.collectFirst({ case d: SClassDef if d.name == md.name => ()}).isDefined
    val hasModule = outerScope.collectFirst({ case d: SValDef if d.name == moduleVarName => ()}).isDefined
    val hasMethod = outerScope.collectFirst({ case d: SMethodDef if d.name == md.name => ()}).isDefined
    hasClass && hasModule && hasMethod
  }

  def isInternalClassOfCompanion(cd: SEntityDef, outerScope: List[SBodyItem]): Boolean = {
    val moduleVarName = cd.name + global.nme.MODULE_VAR_SUFFIX.toString
    if (cd.ancestors.nonEmpty) return false
    val hasClass = outerScope.collectFirst({ case d: SClassDef if d.name == cd.name => ()}).isDefined
    val hasModule = outerScope.collectFirst({ case d: SValDef if d.name == moduleVarName => ()}).isDefined
    val hasMethod = outerScope.collectFirst({ case d: SMethodDef if d.name == cd.name => ()}).isDefined
    hasClass && hasModule && hasMethod
  }

  def returnParsed[R <: NamedDef](scalaSym: SymTreeApi, sdef: R)(implicit ctx: ParseCtx): R = {
    ctx.onParsed(scalaSym, sdef)
    sdef
  }

  def unitDefFromPackageDef(moduleName: String, packageDef: PackageDef)(implicit ctx: ParseCtx): SUnitDef = {
    val packageName = packageDef.pid.toString
    val statements = packageDef.stats
    val imports = statements.collect { case i: Import => importStat(i) }
    val isVirtualized = ctx.isVirtualized

    def makeUnitDef(moduleName: String, cakeImports: List[SImportStat], defs: List[SBodyItem], selfType: Option[SSelfTypeDef], ancestors: List[STypeApply]) = {
      val isDefinedModule = findClassDefByName(
        statements, SUnitDef.moduleTraitName(moduleName)).isDefined
      val classes = defs.collect {
        case c: SClassDef if !(c.hasAnnotation("InternalType") || isInternalClassOfCompanion(c, defs)) => c
      }
      val traits = defs.collect {
        case t: STraitDef if !(t.name.endsWith("Companion") || t.hasAnnotation("InternalType")) => t
      }
      val typeDefs = defs.collect { case t: STpeDef => t }
      val methods = defs.collect { case md: SMethodDef if !isInternalMethodOfCompanion(md, defs) => md }

      SUnitDef(packageName, imports ++ cakeImports, moduleName,
        typeDefs,
        traits, classes, methods,
        selfType, ancestors,
        None, isVirtualized, okEmitOrigModuleTrait = !isDefinedModule)
    }

    val unitDef = if (isVirtualized) {
      val mainTraitTree = statements.collect {
        case cd: ClassDef if cd.mods.isTrait && !cd.name.contains("Module") => cd
      } match {
        case List(only) => only
        case seq => !!!(s"There must be exactly one trait with entity definition in a file, found ${seq.map(_.name.toString)}")
      }
      val moduleName = mainTraitTree.name.toString
      val unitSym = ctx.astContext.newUnitSymbol(packageName, moduleName)

      val ancestors = this.ancestors(unitSym, mainTraitTree.impl.parents)
      val selfType = this.selfType(unitSym, mainTraitTree.impl.self)
      val defs = mainTraitTree.impl.body.flatMap(optBodyItem(unitSym, _, Some(mainTraitTree)))
      val cakeImports = defs.collect { case SImportStat(n,_) => SImportStat(n, true) }

      makeUnitDef(moduleName, cakeImports, defs, selfType, ancestors)
    } else {
      val unitSym = ctx.astContext.newUnitSymbol(packageName, moduleName)
      val defs = statements.filterMap { tree => optBodyItem(unitSym, tree, Some(packageDef)) }
      makeUnitDef(moduleName, Nil, defs, None, Nil)
    }
    returnParsed(packageDef, unitDef)
  }

  def importStat(i: Import, inCake: Boolean = false): SImportStat = {
    SImportStat(i.toString.stripPrefix("import "), inCake)
  }

  def isEvidenceParam(vd: ValDef) = vd.name.toString.startsWith("evidence$")

  def tpeArg(owner: SSymbol, evidenceTypes: List[Tree], tdTree: TypeDef)(implicit ctx: ParseCtx): STpeArg = {
    val bound = tdTree.rhs match {
      case TypeBoundsTree(low, high) =>
        if (high.toString == "_root_.scala.Any")
          None
        else
          optTpeExpr(owner, high)
      case tt: TypeTree => parseType(tt.tpe) match {
        case STpeTypeBounds(_, STpePrimitive("Any", _)) => None
        case STpeTypeBounds(_, hi) => Some(hi)
        case tpe => ???(tdTree)
      }
      case _ => ???(tdTree)
    }
    val contextBounds = evidenceTypes.collect {
      case AppliedTypeTree(tpt, List(arg)) if arg.toString == tdTree.name.toString =>
        Some(tpt.toString)
      case _ => None
    }.flatten
    val tparams = tdTree.tparams.map(tpeArg(owner, evidenceTypes, _))
    val annotations = tdTree.mods.annotations.collect {
      case ExtractAnnotation(name, ts, args) if !AnnotationsToRemove.contains(name) =>
        STypeArgAnnotation(name, ts.map(_.fold(parseType, parseType)), args.map(parseExpr(owner, _)))
    }
    STpeArg(tdTree.name, bound, contextBounds, tparams, tdTree.mods.flags, annotations)
  }

  def tpeArgs(owner: SSymbol, typeParams: List[TypeDef], possibleImplicits: List[ValDef])(implicit ctx: ParseCtx): List[STpeArg] = {
    val evidenceTypes = possibleImplicits.filter(isEvidenceParam(_)).map(_.tpt)
    typeParams.map(tpeArg(owner, evidenceTypes, _))
  }


  val defaultParents = Set("AnyRef", "scala.AnyRef")

  /** Get ancestors excluding default parents */
  def ancestors(owner: SSymbol, trees: List[Tree])(implicit ctx: ParseCtx): List[STypeApply] = {
    trees.map(typeApply(owner, _))
         .filterNot(ta => defaultParents.contains(ta.tpe.name))
  }

  def findCompanion
      (owner: SSymbol, name: String, parentScope: Option[Tree])
      (implicit ctx: ParseCtx): Option[SEntityDef] = {
    val body = parentScope match {
      case None => Nil
      case Some(scope: ImplDef) => scope.impl.body
      case Some(scope: PackageDef) => scope.stats
      case Some(tree) => sys.error(s"Don't know how to findCompanion in $tree")
    }
    body.collectFirst {
      case c: ClassDef if ctx.isVirtualized && c.name.toString == name + "Companion" =>
        if (c.mods.isTrait)
          traitDef(owner, c, parentScope)
        else
          classDef(owner, c, parentScope)
      case c: ClassDef if c.mods.hasModuleFlag && !ctx.isVirtualized && c.name.toString == name =>
        objectDef(owner, c)
      case m: ModuleDef if !ctx.isVirtualized && !m.mods.isSynthetic && m.name.toString == name =>
        objectDef(owner, m)
    }
  }

  def tpeDef(owner: SSymbol, td: TypeDef, parentScope: Option[Tree])(implicit ctx: ParseCtx): STpeDef = {
    val tpeArgs = this.tpeArgs(owner, td.tparams, Nil)
    val rhs = tpeExpr(owner, td.rhs)
    returnParsed(td, STpeDef(owner, td.name, tpeArgs, rhs))
  }

  def valDef(owner: SSymbol, vd: ValDef, parentScope: Option[Tree])(implicit ctx: ParseCtx): SValDef = {
    val tpeRes = optTpeExpr(owner, vd.tpt)
    val isImplicit = vd.mods.isImplicit
    val isLazy = vd.mods.isLazy
    returnParsed(vd, SValDef(owner, vd.name, tpeRes, isLazy, isImplicit, parseExpr(owner, vd.rhs)))
  }

  def traitDef(owner: SSymbol, td: ClassDef, parentScope: Option[Tree])(implicit ctx: ParseCtx): STraitDef = {
    val name = td.name.toString
    val sym = SEntityDefSymbol(owner, name)
    val tpeArgs = this.tpeArgs(sym, td.tparams, Nil)
    val ancestors = this.ancestors(sym, td.impl.parents)
    val body = td.impl.body.flatMap(optBodyItem(sym, _, Some(td)))
    val selfType = this.selfType(sym, td.impl.self)
    val companion = findCompanion(owner, name, parentScope) // note: using owner, not sym
    val annotations = parseAnnotations(td)((n, ts, as) =>
      Some(SEntityAnnotation(n.lastComponent('.'), ts.map(parseType), as.map(parseExpr(owner, _))))
    )
    returnParsed(td, STraitDef(owner, name, tpeArgs, ancestors, body, selfType, companion, annotations))
  }

  def classDef(owner: SSymbol, cd: ClassDef, parentScope: Option[Tree])(implicit ctx: ParseCtx): SClassDef = {
    val name = cd.name.toString
    val sym = SEntityDefSymbol(owner, name)
    val ancestors = this.ancestors(sym, cd.impl.parents)
    val constructor = (cd.impl.body.collect {
      case dd: DefDef if dd.name == nme.CONSTRUCTOR => dd
    }) match {
      case Seq(only) => only
      case seq => !!!(s"Class ${cd.name} should have 1 constructor but has ${seq.length} constructors", cd)
    }
    // TODO simplify
    val (args, implicitArgs) = constructor.vparamss match {
      case Seq() =>
        (classArgs(sym, List.empty), classArgs(sym, List.empty))
      case Seq(nonImplicitConArgs) =>
        (classArgs(sym, nonImplicitConArgs), classArgs(sym, List.empty))
      case Seq(nonImplicitConArgs, implicitConArgs) =>
        (classArgs(sym, nonImplicitConArgs), classArgs(sym, implicitConArgs))
      case seq => !!!(s"Constructor of class ${cd.name} has more than 2 parameter lists, not supported")
    }
    val tpeArgs = this.tpeArgs(sym, cd.tparams, constructor.vparamss.lastOption.getOrElse(Nil))
    val body = cd.impl.body.flatMap(optBodyItem(sym, _, Some(cd)))
    val selfType = this.selfType(sym, cd.impl.self)
    val isAbstract = cd.mods.hasAbstractFlag
    val companion = findCompanion(owner, name, parentScope)
    val annotations = parseAnnotations(cd)((n, ts, as) =>
      Some(SEntityAnnotation(n, ts.map(parseType), as.map(parseExpr(owner, _))))
    )
    returnParsed(cd, SClassDef(owner, cd.name,
      tpeArgs, args, implicitArgs, ancestors, body, selfType, companion, isAbstract, annotations))
  }

  def objectDef(owner: SSymbol, od: ImplDef)(implicit ctx: ParseCtx): SObjectDef = {
    assert(!od.isInstanceOf[ClassDef] || od.mods.hasModuleFlag)
    val sym = SEntityDefSymbol(owner, od.name)
    val ancestors = this.ancestors(sym, od.impl.parents)
    val body = od.impl.body.flatMap(optBodyItem(sym, _, Some(od)))
    returnParsed(od, SObjectDef(owner, od.name, ancestors, body))
  }

  def classArg(owner: SSymbol, vd: ValDef)(implicit ctx: ParseCtx): SClassArg = {
    val tpe = tpeExpr(owner, vd.tpt)
    val default = optExpr(owner, vd.rhs)
    val isOverride = vd.mods.isAnyOverride
    val isVal = vd.mods.isParamAccessor
    val annotations = parseAnnotations(vd)((n, ts, as) =>
      Some(SArgAnnotation(n, ts.map(parseType), as.map(parseExpr(owner, _))))
    )
    val isTypeDesc = TypeDescTpe.unapply(tpe).isDefined
    SClassArg(owner, vd.mods.isImplicit, isOverride, isVal, vd.name, tpe, default, annotations, isTypeDesc)
  }

  def classArgs(owner: SSymbol, vds: List[ValDef])(implicit ctx: ParseCtx): SClassArgs = SClassArgs(vds.filter(!isEvidenceParam(_)).map(classArg(owner, _)))

  def traitCall(owner: SSymbol, tree: Tree)(implicit ctx: ParseCtx): Option[STraitCall] = tree match {
    case ident: Ident =>
      Some(STraitCall(ident.name, List()))
    case select: Select =>
      Some(STraitCall(select.name, List()))
    case AppliedTypeTree(tpt, args) =>
      Some(STraitCall(tpt.toString, args.map(tpeExpr(owner, _))))
    case tt: TypeTree =>
      val parsedType = parseType(tt.tpe)
      val tc = parsedType match {
        case call: STraitCall => call
        case STpePrimitive(name, _) => STraitCall(name, List())
        case _ =>
          throw new IllegalArgumentException(parsedType.toString)
      }
      Some(tc)
    case tree => None
  }

  def typeApply(owner: SSymbol, tree: Tree)(implicit ctx: ParseCtx): STypeApply = tree match {
    case Apply(fun, args) =>
      traitCall(owner, fun) match {
        case Some(tc) => STypeApply(tc, args.map(parseExpr(owner, _)))
        case None => ???(fun)
      }
    case t =>
      traitCall(owner, t) match {
        case Some(tc) => tc.toTypeApply
        case None => ???(t)
      }
  }

  def isInternal(md: ValOrDefDef): Boolean = {
    val as = parseAnnotations(md)((n, _, _) => Some(SMethodAnnotation(n, Nil, Nil)))
    as.exists(_.annotationClass == "Internal") ||
    md.name.toString.startsWith("__") && md.name.toString.endsWith("__")
  }

  def isExplicitMethod(md: DefDef): Boolean = {
    if (nme.isConstructorName(md.name)) false
    else if (md.mods.isSynthetic) false
    else if (md.mods.isCaseAccessor) false
    else if (md.mods.isParamAccessor) false
    else if (isInternal(md)) false
    else true
  }

  def optBodyItem(owner: SSymbol, tree: Tree, parentScope: Option[Tree])(implicit ctx: ParseCtx): Option[SBodyItem] = tree match {
    case i: Import =>
      Some(importStat(i))
    case md: DefDef =>
      if (isExplicitMethod(md))
        md.tpt match {
          case AppliedTypeTree(tpt, _) if tpt.toString == "Elem" =>
            Some(methodDef(owner, md, isElem = true))
          case _ =>
            Some(methodDef(owner, md))
        }
      else
        None
    case vd: ValDef =>
      if (!isInternal(vd))
        vd match {
          case vd if vd.mods.isParamAccessor => None
          case _ =>
            Some(valDef(owner, vd, parentScope))
        }
      else
        None
//    case LabelDef(name, params, rhs) =>
//      SLabelDef(owner, name, params.map(_.toString()), parseExpr(owner, rhs))
    case td: TypeDef =>
      Some(tpeDef(owner, td, parentScope))
    case td: ClassDef if td.mods.isTrait =>
      Some(traitDef(owner, td, parentScope))
    case cd: ClassDef if cd.mods.hasModuleFlag =>
      Some(objectDef(owner, cd))
    case cd: ClassDef if !cd.mods.isTrait =>
      // don't include implicit conversion classes
      if (!cd.mods.isImplicit)
        Some(classDef(owner, cd, parentScope))
      else
        None
    case od: ModuleDef =>
      Some(objectDef(owner, od))
    case EmptyTree =>
      None
    // calls in constructor
    case Select(_, _) =>
      None
    case Apply(_, _) =>
      None
    case tree => ???(tree)
  }

  object ExtractAnnotation {
    def unapply(a: Tree): Option[(String, List[Either[Tree,Type]], List[Tree])] = a match {
      case Apply(Select(New(Ident(ident)), nme.CONSTRUCTOR), args) =>
        Some((ident, Nil, args))
      case Apply(Select(New(tree @ AppliedTypeTree(tpt, tyArgs)), nme.CONSTRUCTOR), args) =>
        val ts = tyArgs.map {
          case id @ Ident(name) => id
          case t => !!!(s"Unsupported type argument $t of annotation $a")
        }
        Some((tpt.toString, ts.map(Left(_)), args))
      case Apply(Select(New(tree: TypeTree), nme.CONSTRUCTOR), args) =>
        val tpeSym = tree.tpe.typeSymbol
        val n = tpeSym.nameString
        val tpeArgs = tree.tpe.typeArgs
        Some((n, tpeArgs.map(Right(_)), args))
      case Apply(Select(New(tpt), nme.CONSTRUCTOR), args) =>
        Some((tpt.toString(), Nil, args))
      case _ => None
    }
  }

  def parseAnnotations[A <: SAnnotation](md: MemberDef)(p: (String, List[Either[Tree, Type]], List[Tree]) => Option[A]): List[A] = {
    val as = md.collectAnnotations
    val annotations = as.filterMap {
      case ExtractAnnotation(name, ts, args) => p(name, ts, args)
      case a => !!! (s"Cannot parse annotation $a of MemberDef $md")
    }
    annotations
  }

  class HasAnnotation(annClass: String) {
    def unapply(md: MemberDef): Option[List[Tree]] =
      md.collectAnnotations.collectFirst {
        case ExtractAnnotation(name, _, args) if name == annClass => args
      }
  }

  val OverloadIdAnnotation  = new HasAnnotation("OverloadId")
  val ReifiedAnnotation     = new HasAnnotation(ReifiedTypeArgAnnotation)
  val HasNeverInlineAnnotation = new HasAnnotation(NeverInlineAnnotation)

  def methodDef(owner: SSymbol, md: DefDef, isElem: Boolean = false)(implicit ctx: ParseCtx) = try {
    val methodSym = SEntityItemSymbol(owner, md.name, DefType.Def)
    val tpeArgs = this.tpeArgs(methodSym, md.tparams, md.vparamss.lastOption.getOrElse(Nil))
    val args0 = md.vparamss.map(methodArgs(methodSym, _))
    val args = if (!args0.isEmpty && args0.last.args.isEmpty) args0.init else args0
    val tpeRes = optTpeExpr(methodSym, md.tpt)
    val isImplicit = md.mods.isImplicit
    val isOverride = md.mods.isOverride
    val optOverloadId = md match {
      case OverloadIdAnnotation(List(Literal(Constant(overloadId)))) =>
        Some(overloadId.toString)
      case OverloadIdAnnotation(List(AssignOrNamedArg(Ident(TermName("value")), Literal(Constant(overloadId))))) =>
        Some(overloadId.toString)
      case _ =>
        None
    }
    val annotations = parseAnnotations(md) {
      case ("throws", ts, as) =>
        Some(SMethodAnnotation("throws", ts.map(parseType), Nil))
      case (n, ts, as) if !AnnotationsToRemove.contains(n) =>
        Some(SMethodAnnotation(n, ts.map(parseType), as.map(parseExpr(methodSym, _))))
      case _ => None
    }
    val optNeverInline = annotations.collectFirst { case a @ SMethodAnnotation(NeverInlineAnnotation, _,_) => a }
    val rhs = optNeverInline match {
      case Some(_) =>
        Ident(TermName("delayInvoke"))
      case None =>
        md.rhs
    }
    val optBody: Option[SExpr] = optExpr(methodSym, rhs)
    val isTypeDesc = md.tpt match {
      case AppliedTypeTree(tpt, _) if Set("Elem", "Cont").contains(tpt.toString) =>
        true
      case tpt =>
        tpt.toString == "TypeDesc"
    }

    returnParsed(md, SMethodDef(owner, md.name, tpeArgs, args, tpeRes, isImplicit, isOverride,
      optOverloadId, annotations, optBody, isTypeDesc))
  } catch {
    case e: Throwable =>
      throw new RuntimeException(s"Cannot parse MethodDef $md from $owner", e)
  }

  def methodArgs(owner: SSymbol, vds: List[ValDef])(implicit ctx: ParseCtx): SMethodArgs = vds match {
    case Nil => SMethodArgs(List.empty)
    case vd :: _ =>
      SMethodArgs(vds.filter(!isEvidenceParam(_)).map(methodArg(owner, _)))
  }

  def optTpeExpr(owner: SSymbol, tree: Tree)(implicit ctx: ParseCtx): Option[STpeExpr] = {
    tree match {
      case _ if tree.isEmpty => None
      case _: ExistentialTypeTree => None
      case tree => Some(tpeExpr(owner, tree))
    }
  }

  def formAppliedTypeTree(fullName: String, shortName: String, argTpeExprs: List[STpeExpr]) = {
    val tuplePattern = """^(_root_.)?scala.Tuple(\d+)$"""
    val funcPattern = """^(_root_.)?scala.Function(\d+)$"""

    if (Pattern.matches(tuplePattern, fullName))
      STpeTuple(argTpeExprs)
    else if (Pattern.matches(funcPattern, fullName)) {
      argTpeExprs.length match {
        case 1 =>
          STraitCall("Function0", argTpeExprs)
        case 2 =>
          val domainTpeExpr = argTpeExprs(0)
          STpeFunc(domainTpeExpr, argTpeExprs.last)
        case n if n > 2 =>
          val domainTpeExpr = STpeTuple(argTpeExprs.init)
          STpeFunc(domainTpeExpr, argTpeExprs.last)
        case _ =>
          !!!(s"fullName=$fullName shortName=$shortName argTpeExprs=$argTpeExprs")
      }
    } else
      STraitCall(shortName, argTpeExprs)
  }

  object IsRepeatedArgType {
    def unapply(tpt: Tree): Boolean = tpt.toString.endsWith("<repeated>")
  }

  object IsByNameArgType {
    def unapply(tpt: Tree): Boolean = tpt.toString.endsWith("<byname>")
  }

  def tpeExpr(owner: SSymbol, tree: Tree)(implicit ctx: ParseCtx): STpeExpr = tree match {
    case EmptyTree => STpeEmpty()
    case ident: Ident =>
      val name = ident.name.toString
      STpePrimitives.getOrElse(name, STraitCall(name, List()))
    case select: Select =>
        STraitCall(select.name, List())
    case AppliedTypeTree(tpt, args) =>
      val argTpeExprs = args.map(tpeExpr(owner, _))
      tpt match {
        case IsByNameArgType() =>
          STraitCall("Thunk", argTpeExprs)
        case _ =>
          val genericTypeString = if (IsRepeatedArgType.unapply(tpt)) "RepeatedArg" else tpt.toString
          formAppliedTypeTree(genericTypeString, genericTypeString, argTpeExprs)
      }
    case tq"$tpt @$annot" => STpeAnnotated(tpeExpr(owner, tpt), annot.toString)
    case TypeBoundsTree(lo, hi) => STpeTypeBounds(tpeExpr(owner, lo), tpeExpr(owner, hi))
    case SingletonTypeTree(ref) => STpeSingleton(parseExpr(owner, ref))
    case SelectFromTypeTree(qualifier, TypeName(name)) => STpeSelectFromTT(tpeExpr(owner, qualifier), name)
    case tq"..$parents { ..$defns }" => STpeCompound(parents.map(tpeExpr(owner, _)), defns.flatMap(defn => optBodyItem(owner, defn, None)))
    case tq"$tpt forSome { ..$defns }" => STpeExistential(tpeExpr(owner, tpt), defns.flatMap(defn => optBodyItem(owner, defn, None)))
    case Bind(TypeName(name), body) => STpeBind(name, tpeExpr(owner, body))
    case tt: TypeTree =>
      tt.original match {
        case AppliedTypeTree(IsRepeatedArgType(), args) =>
          tpeExpr(owner, tt.original)
        case _ =>
          parseType(tt.tpe)
      }

    case tree => ???(tree)
  }

  def methodArg(owner: SSymbol, vd: ValDef)(implicit ctx: ParseCtx): SMethodArg = {
    val tpe = tpeExpr(owner, vd.tpt)
    val default = optExpr(owner, vd.rhs)
    val annotations = parseAnnotations(vd)((n, ts, as) =>
      Some(SArgAnnotation(n, ts.map(parseType), as.map(parseExpr(owner, _))))
    )
    val isOverride = vd.mods.isAnyOverride
    val isTypeDesc = tpe match {
      case STraitCall(tname, _) if tname == "Elem" || tname == "Cont" => true
      case _ => false
    }
    SMethodArg(vd.mods.isImplicit, isOverride, vd.name, tpe, default, annotations, isTypeDesc)
  }

  def selfType(owner: SSymbol, vd: ValDef)(implicit ctx: ParseCtx): Option[SSelfTypeDef] = {
    val components = vd.tpt match {
      case t if t.isEmpty =>
        Nil
      case CompoundTypeTree(Template(ancestors, _, _)) =>
        ancestors.map(tpeExpr(owner, _))
      case t =>
        List(tpeExpr(owner, t))
    }

    if (components.isEmpty)
      None
    else
      Some(SSelfTypeDef(vd.name.toString, components))
  }

  def optExpr(owner: SSymbol, tree: Tree)(implicit ctx: ParseCtx): Option[SExpr] = {
    if (tree.isEmpty)
      None
    else
      Some(parseExpr(owner, tree))
  }

  def tree2Type(tree: Tree): Option[STpeExpr] = tree.tpe match {
    case null => None
    case tpe => Some(parseType(tpe))
  }


  def applyArrayFill(f: SExpr, tyArg: Option[STpeExpr], arg1: SExpr, arg2: SExpr) = {
    SApply(f, tyArg.toList, List(List(arg1, SApply(SIdent("Thunk"), Nil, List(List(arg2))))))
  }

  def applyArray1(methodName: String, xs: SExpr, ts: List[STpeExpr], arg: SExpr) = {
    SApply(SSelect(xs, methodName), ts, List(List(arg)))
  }

  def applyArray2(methodName: String, xs: SExpr, ts: List[STpeExpr], arg1: SExpr, arg2: SExpr) = {
    SApply(SSelect(xs, methodName), ts, List(List(arg1, arg2)))
  }

  private lazy val arrayImplicitWrappers: Set[TermName] = Set(
    TermName("genericWrapArray"),
    TermName("genericArrayOps"),
    TermName("wrapRefArray"),
    TermName("wrapDoubleArray"),
    TermName("doubleArrayOps"),
    TermName("intArrayOps"),
    TermName("refArrayOps")
  )
  def isArrayImplicitWrapperName(ops: TermName) = arrayImplicitWrappers.contains(ops.asInstanceOf[TermName])

  object IsArrayImplicitWrapper {
    def unapply(tree: Tree): Option[Tree] = tree match {
      case obj @ q"$_.Predef.$ops($arg)" if isArrayImplicitWrapperName(ops) => Some(arg)
      case obj @ q"$_.Predef.$ops[$tpe]($arg)" if isArrayImplicitWrapperName(ops) => Some(arg)
      case _ => None
    }
  }

  object IsArrayWrapperMethod {
    def unapply(tree: Tree): Option[(Tree, Name)] = tree match {
      case q"${obj @ IsArrayImplicitWrapper(_)}.$m" => Some((obj, m))
      case _ => None
    }
  }

  object ApplyArrayWrapperMethod {
    def unapply(tree: Tree): Option[(Tree, Name, List[Tree], List[Tree])] = tree match {
      case Apply(TypeApply(IsArrayWrapperMethod(obj, m), tyArgs), args) =>
        Some((obj, m, tyArgs, args))
      case Apply(IsArrayWrapperMethod(obj, m), args) =>
        Some((obj, m, Nil, args))
      case Apply(ApplyArrayWrapperMethod(obj, m, tyArgs, args), args2) =>
        Some((obj, m, tyArgs, args ::: args2))
      case _ => None
    }
  }
  object TupleTypeTree {
    def unapply(tree: TypeTree): Option[TypeName] = tree.symbol match {
      case cs: ClassSymbol if cs.name == TypeName("Tuple2") => Some(cs.name)
      case _ => None
    }
  }

  private val arrayMethodsWith1Arg = Set("map", "zip", "foreach", "exists", "forall", "filter")
  private val arrayMethodsWith2Args = Set("foldLeft", "slice")

  def parseExpr(owner: SSymbol, tree: Tree)(implicit ctx: ParseCtx): SExpr = tree match {
    case q"${f @ q"scala.Array.fill"}[$tpe]($arg1)($arg2)($_)" =>
      applyArrayFill(parseExpr(owner, f), Some(parseType(tpe.tpe)), parseExpr(owner, arg1), parseExpr(owner, arg2))
    case ApplyArrayWrapperMethod(obj, m, tyArgs, args) =>
      m.decoded match {
        case name if arrayMethodsWith1Arg.contains(name) =>
          applyArray1(name, parseExpr(owner, obj), Nil,  parseExpr(owner, args(0)))
        case name if arrayMethodsWith2Args.contains(name) =>
          applyArray2(name, parseExpr(owner, obj), Nil,  parseExpr(owner, args(0)), parseExpr(owner, args(1)))
        case _ =>
          !!!(s"Don't know how to ApplyArrayWrapperMethod $m", tree)
      }
    case IsArrayImplicitWrapper(arg) => parseExpr(owner, arg)
    case EmptyTree => SEmpty(tree2Type(tree))
    case Literal(Constant(c)) => SConst(c, tree2Type(tree))
    case Ident(TermName(name)) => SIdent(name, tree2Type(tree))
    case q"$left = $right" => SAssign(parseExpr(owner, left), parseExpr(owner, right), tree2Type(tree))
    case q"$name.super[$qual].$field" => SSuper(name, qual, field, tree2Type(tree))
    case q"$expr.$tname" => SSelect(parseExpr(owner, expr), tname, tree2Type(tree))
    case Apply(Select(New(TupleTypeTree(tn)), termNames.CONSTRUCTOR), args) =>
      SConstr(tn.toString(), args.map(parseExpr(owner, _)), tree2Type(tree))
    case Apply(Select(New(tpe), termNames.CONSTRUCTOR), args) =>
      val name = if (tpe.symbol == NoSymbol)
        tpe.toString
      else
        tpe.symbol.nameString
      SConstr(name, args.map(parseExpr(owner, _)), tree2Type(tree))
    case Apply(Select(Ident(TermName("scala")), TermName(tuple)), args) if tuple.startsWith("Tuple") =>
      STuple(args.map(parseExpr(owner, _)), tree2Type(tree))
    case Block(init, last) => SBlock(init.map(parseExpr(owner, _)), parseExpr(owner, last), tree2Type(tree))
    case q"$mods val $tname: $tpt = $expr" =>
      SValDef(owner, tname, optTpeExpr(owner, tpt), mods.isLazy, mods.isImplicit, parseExpr(owner, expr))
    case q"if ($cond) $th else $el" =>
      SIf(parseExpr(owner, cond), parseExpr(owner, th), parseExpr(owner, el), tree2Type(tree))
    case q"$expr: $tpt" => SAscr(parseExpr(owner, expr), tpeExpr(owner, tpt), tree2Type(tree))
    case q"(..$params) => $expr" =>
      SFunc(params.map(param => parseExpr(owner, param).asInstanceOf[SValDef]), parseExpr(owner, expr), tree2Type(tree))
    case q"$tpname.this" => SThis(tpname, tree2Type(tree))
    case q"$expr: @$annot" => SAnnotated(parseExpr(owner, expr), annot.toString, tree2Type(tree))
    case TypeApply(fun: Tree, args: List[Tree]) =>
      SExprApply(parseExpr(owner, fun), args.map(tpeExpr(owner, _)), tree2Type(tree))
    case q"$expr match { case ..$cases } " => parseMatch(owner, expr, cases)
    case q"{ case ..$cases }" => parseMatch(owner, EmptyTree, cases)
    case Apply(TypeApply(fun, targs), args) =>
      SApply(parseExpr(owner, fun), targs.map(tpeExpr(owner, _)), List(args.map(parseExpr(owner, _))), tree2Type(tree))
    case Apply(fun, args) =>
      SApply(parseExpr(owner, fun), Nil, List(args.map(parseExpr(owner, _))), tree2Type(tree))
    case bi => optBodyItem(owner, bi, None) match {
      case Some(item) => item
      case None => throw new NotImplementedError(s"parseExpr($owner): Error parsing of ${showRaw(bi)}")
    }
  }

  def parseMatch(owner: SSymbol, expr: Tree, cases: List[CaseDef])(implicit ctx: ParseCtx) = {
    SMatch(parseExpr(owner, expr), cases.map{_ match {
      case cq"$pat if $guard => $body" => SCase(parsePattern(owner, pat), parseExpr(owner, guard), parseExpr(owner, body))
      case c => throw new NotImplementedError(s"parseExpr($owner): match {case ${showRaw(c)}")
    }})
  }

  object WildcardPattern {
    def unapply(pat: Tree): Boolean = pat match {
      case Bind(nme.WILDCARD, WildcardPattern()) => true
      case Star(WildcardPattern())               => true
      case x: Ident                              => treeInfo.isVarPattern(x)
      case Alternative(ps)                       => ps forall unapply
      case EmptyTree                             => true
      case _                                     => false
    }
  }

  def parsePattern(owner: SSymbol, pat: Tree)(implicit ctx: ParseCtx): SPattern = pat match {
    case WildcardPattern() => SWildcardPattern()
    case Apply(fun, pats) => SApplyPattern(parseExpr(owner, fun), pats.map(parsePattern(owner, _)))
    case Typed(Ident(termNames.WILDCARD), tpe) => STypedPattern(tpeExpr(owner, tpe))
    case Bind(TermName(name), expr) => SBindPattern(name, parsePattern(owner, expr))
    case Literal(Constant(c)) => SLiteralPattern(SConst(c))
    case Ident(id) => SStableIdPattern(SIdent(id.toString))
    case Select(qual, name) => SSelPattern(parseExpr(owner, qual), name.toString)
    case Alternative(alts) => SAltPattern(alts.map(parsePattern(owner, _)))
    case _ => throw new NotImplementedError(s"parsePattern: ${showRaw(pat)}")
  }

  def parseType(tree: Either[Tree, Type]): STpeExpr = tree.fold(parseType, parseType)
  def parseType(tree: Tree): STpeExpr = tree match {
    case Ident(name) => STraitCall(name, Nil)
    case _ => !!!(s"Don't know how to STpeExpr from tree $tree")
  }

  def parseType(tpe: Type): STpeExpr = tpe match {
    case NoType | NoPrefix => STpeEmpty()
    case const: ConstantType => parseType(const.underlying) //STpeConst(SConst(const.value.value, Some(parseType(const.underlying))))
    case thisType: ThisType => STpeThis(thisType.sym.nameString, parseType(thisType.underlying))
    case tref: TypeRef if global.definitions.isByNameParamType(tref) =>
       val ty = parseType(tref.args(0))
       STraitCall("Thunk", List(ty))
    case tref: TypeRef => parseTypeRef(tref)
    case single: SingleType => STpeSingle(parseType(single.pre), single.sym.nameString, parseType(single.underlying))
    case TypeBounds(lo, hi) => STpeTypeBounds(parseType(lo), parseType(hi))
    case ExistentialType(quant, under) =>
      val quantified = quant map(q => STpeDef(SNoSymbol, q.nameString, Nil, STpeEmpty()))
      val underlying = parseType(under)
      STpeExistential(underlying, quantified)
    case _: MethodType | _: NullaryMethodType => parseMethodType(Nil, tpe)
    case PolyType(tparams, resultType) => resultType match {
      case _: MethodType | _: NullaryMethodType => parseMethodType(tparams, resultType)
      case _ => throw new NotImplementedError(showRaw(tpe, printTypes = Some(true)))
    }
    case annot: AnnotatedType => parseType(annot.underlying)
    case tpe =>
      throw new NotImplementedError(showRaw(tpe, printTypes = Some(true)))
  }

  def parseMethodType(tparams: List[Symbol], m: Type): STpeMethod = {
    val method = m //uncurry.transformInfo(m.typeSymbol, m)
    val typeParams = tparams.map(_.nameString)
    val params = method.paramss.map(_.map(param => parseType(param.tpe)))
    val res = parseType(method.finalResultType)

    STpeMethod(typeParams, params, res)
  }

  def parseTypeRef(tref: TypeRef): STpeExpr = {
    STpePrimitives.get(tref.sym.nameString) match {
      case Some(prim) => prim
      case None =>
        val fullName = tref.sym.fullNameString
        val shortName = tref.sym.nameString
        val args = tref.args map parseType

        formAppliedTypeTree(fullName, shortName, args)
    }
  }
}
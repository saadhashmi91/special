package scalan.meta

import scala.tools.nsc.Global
import scalan.meta.ScalanAst.{STraitCall, SClassArg, SLiteralPattern, STpeDef, SWildcardPattern, SUnitDef, SValDef, STypedPattern, STpeArgs, STpeExpr, STpeFunc, SSelPattern, SExpr, STpeArg, STpeEmpty, STpeConst, STuple, SClassDef, SCase, SIf, STpeThis, SIdent, SStableIdPattern, SBindPattern, STpeTuple, RepeatedArgType, SMethodArg, SApplyPattern, SObjectDef, SSelfTypeDef, STpeSingleton, STraitDef, SMethodDef, SAssign, SApply, SFunc, SConst, SEmpty, STypeApply, SBlock, STpeSelectFromTT, SExprApply, SClassArgs, SAscr, SThis, SMethodArgs, SMatch, STpeTypeBounds, SImportStat, SPattern, STpePrimitive, SAnnotated, SBodyItem, STpeAnnotated, SEntityDef, SConstr, SAnnotation, SSuper, STpeExistential, SSelect, SAltPattern}
import scalan.meta.ScalanAstTransformers.filterInternalAnnot
import scalan.meta.Symbols.SUnitDefSymbol
import scalan.util.ScalaNameUtil.PackageAndName

trait ScalanGens[+G <: Global] { self: ScalanParsers[G] =>
  import global._

  case class GenCtx(context: AstContext, isVirtualized: Boolean, toRep: Boolean = true)

  def createModuleTrait(unitSym: SUnitDefSymbol) = {
    val mainModuleName = unitSym.unitName.name
    val mt = STraitDef(
      owner = unitSym,
      name = SUnitDef.moduleTraitName(mainModuleName),
      tpeArgs = Nil,
      ancestors = List(STraitCall(s"impl.${mainModuleName}Defs"), STraitCall("scala.wrappers.WrappersModule")).map(STypeApply(_)),
      body = Nil, selfType = None, companion = None)
    mt
  }

  def genRefTree(packageName: String): RefTree = {
    val names = packageName.split('.').toList
    names match {
      case h :: (t @ List(_, _*)) => t.foldLeft[RefTree](Ident(h)) ((qualifier, n) => Select(qualifier, n))
      case h :: Nil => Ident(h)
      case _ => sys.error(s"Invalid packageName $packageName")
    }
  }

  /** Generates PackageDef (Scala AST) for the given SUnitDef. */
  def genPackageDef(unit: SUnitDef, isVirtualized: Boolean = false)(implicit ctx: AstContext): PackageDef = {
    implicit val genCtx = GenCtx(ctx, isVirtualized, toRep = !isVirtualized)
    genPackageDef(unit)(genCtx)
  }

  def genPackageDef(unit: SUnitDef)(implicit ctx: GenCtx): PackageDef = {
      val ref = genRefTree(unit.packageName)
      val imports = unit.imports.filterNot(_.inCake).map(genImport(_))
      val moduleBody = List[Tree](genModuleTrait(unit))
      PackageDef(ref,
        imports ++
            moduleBody
      )
  }

  def genModuleTrait(unit: SUnitDef)(implicit ctx: GenCtx): Tree = {
    val methods = unit.methods.map(m => genMethod(m)(ctx.copy(toRep = !m.isTypeDesc)))
    val imports = unit.imports.filter(_.inCake).sortBy(i => i.name)
    val newstats =
      imports.map(genImport) :::
      unit.typeDefs.map(genTypeDef) :::
      unit.traits.map(genTrait) :::
      (genConcreteClasses(unit.classes) ++ genCompanions(unit) ++ methods)
    val newSelf = genModuleSelf(unit)
    val name = TypeName(unit.name)
    val moduleParents = genParents(unit.ancestors)
    val res = q"trait $name extends ..$moduleParents { $newSelf => ..$newstats }"
    res
  }


  def genCompanions(module: SUnitDef)(implicit ctx: GenCtx): List[Tree] = {
    val fromEntities = module.traits.map(e => genCompanion(e.companion))
    val fromClasses = module.classes.map(clazz => genCompanion(clazz.companion))
    fromEntities ::: fromClasses
  }

  def genCompanion(comp: Option[SEntityDef])(implicit ctx: GenCtx): Tree = comp match {
    case Some(comp) => comp match {
      case t: STraitDef => genTrait(t)
      case c: SClassDef => genClass(c)
      case _ => throw new NotImplementedError(s"genCompanion: $comp")
    }
    case None => EmptyTree
  }



  def genParents(ancestors: List[STypeApply])(implicit ctx: GenCtx): List[Tree] = {
    val parents = Select(Ident("scala"), TypeName("AnyRef"))

    parents :: ancestors.map { anc =>
      val tpt = genTypeName(anc.tpe.name)
      val tpts = anc.tpe.args.map(genTypeExpr)
      val args = anc.ts.map(genExpr)
      val fun = tq"$tpt[..$tpts]"
      val res = if (args.isEmpty) fun else Apply(fun, args)
      res
    }
  }

  def genSelf(selfType: Option[SSelfTypeDef])(implicit ctx: GenCtx) = selfType match {
    case Some(SSelfTypeDef(name, Nil)) =>
      val flags = Flag.PRIVATE | Flag.LAZY
      val mods = Modifiers(NoFlags, tpnme.EMPTY, List())
      ValDef(mods, TermName(name), EmptyTree, EmptyTree)
    case Some(selfDef: SSelfTypeDef) => q"val self: ${genTypeByName(selfDef.tpe)}"
    case None => noSelfType
  }

  def genModuleSelf(module: SUnitDef)(implicit ctx: GenCtx): Tree = {
    val tpeName = module.selfType match {
      case Some(st) if !st.components.isEmpty => st.components.head.name
      case _ => module.name + "Dsl"
    }
    val selfType = genTypeByName(tpeName)
    val res = q"val self: $selfType"
    res
  }

  def genTypeExpr(tpeExpr: STpeExpr)(implicit ctx: GenCtx): Tree = tpeExpr match {
    case STpeEmpty() => tq""
    case STpeConst(const, _) => tq"${ConstantType(Constant(const.c))}"
    case STpePrimitive(name: String, _) => tq"${TypeName(name)}"
    case STraitCall(name: String, tpeSExprs: List[STpeExpr]) =>
      val targs = tpeSExprs.map(genTypeExpr)
      tq"${TypeName(name)}[..$targs]"
    case STpeTypeBounds(lo: STpeExpr, hi: STpeExpr) =>
      TypeBoundsTree(genTypeExpr(lo), genTypeExpr(hi))
    case STpeTuple(items: List[STpeExpr]) => genTuples(items)
    case STpeFunc(domain: STpeExpr, range: STpeExpr) =>
      val tpt = genTypeSel("scala", "Function1")
      val tpts = genTypeExpr(domain) :: genTypeExpr(range) :: Nil
      tq"$tpt[..$tpts]"
    case STpeSingleton(ref) => tq"${genExpr(ref)}.type"
    case STpeSelectFromTT(qualifier, name) => tq"${genTypeExpr(qualifier)}#${TypeName(name)}"
    case STpeAnnotated(tpt, annot) => tq"${genTypeExpr(tpt)} @${TypeName(annot)}"
    case STpeExistential(tpt, defns) => tq"${genTypeExpr(tpt)} forSome { ..${defns.map(genBodyItem)} }"
    case _ => throw new NotImplementedError(s"genTypeExpr($tpeExpr)")
  }

  def genTypeSel(ref: String, name: String)(implicit ctx: GenCtx) = {
    Select(Ident(ref), TypeName(name))
  }

  def genTuple2(first: Tree, second: Tree)(implicit ctx: GenCtx): Tree = {
    val tpt = genTypeSel("scala", "Tuple2")
    val tpts = first :: second :: Nil

    tq"$tpt[..$tpts]"
  }

  def genTuples(elems: List[STpeExpr])(implicit ctx: GenCtx): Tree = elems match {
    case x :: y :: Nil => genTuple2(genTypeExpr(x), genTypeExpr(y))
    case x :: xs => genTuple2(genTypeExpr(x), genTuples(xs))
    case Nil => throw new IllegalArgumentException("Tuple must have at least 2 elements.")
  }

  def genExpr(expr: SExpr)(implicit ctx: GenCtx): Tree = expr match {
    case empty: SEmpty => q""
    case const: SConst =>
      val constTree = Literal(Constant(const.c))
      if (!ctx.isVirtualized && ctx.toRep)
        const.exprType match {
          case Some(t) =>
            q"toRep($constTree.asInstanceOf[${genTypeExpr(t)}])"
          case None =>
            q"toRep($constTree)"
        }
      else
        constTree
    case ident: SIdent => Ident(TermName(ident.name))
    case assign: SAssign => q"${genExpr(assign.left)} = ${genExpr(assign.right)}"
    case select: SSelect => select.expr match {
      case _: SEmpty | SThis("scala", _) => q"${TermName(select.tname)}"
      case _ => q"${genExpr(select.expr)}.${TermName(select.tname)}"
    }
    case apply: SApply =>
      val typeArgs = apply.ts.map(genTypeExpr)
      val valArgss = apply.argss.map(_.map(genExpr))
      apply.fun match {
        case SSelect(SSelect(SIdent("scala",_), "Tuple2",_), "apply",_) =>
          q"Pair[..$typeArgs](...$valArgss)"
        case _ => q"${genExpr(apply.fun)}[..$typeArgs](...$valArgss)"
      }
    case block: SBlock => Block(block.init.map(genExpr), genExpr(block.last))
    case sIf: SIf => q"IF (${genExpr(sIf.cond)}) THEN {${genExpr(sIf.th)}} ELSE {${genExpr(sIf.el)}}"
    case ascr: SAscr =>
      val tpe = if (ctx.isVirtualized) genTypeExpr(ascr.pt) else repTypeExpr(ascr.pt)
      q"${genExpr(ascr.expr)}: $tpe"
    case constr: SConstr => genConstr(constr)
    case func: SFunc => genFunc(func)
    case sThis: SThis =>
      q"${TypeName(sThis.typeName)}.this"
    case sSuper: SSuper =>
      q"${TypeName(sSuper.name)}.super[${TypeName(sSuper.qual)}].${TermName(sSuper.field)}"
    case annotated: SAnnotated =>
      q"${genExpr(annotated.expr)}: @${TypeName(annotated.annot)}"
    case exprApply: SExprApply =>
      q"${genExpr(exprApply.fun)}[..${exprApply.ts.map(genTypeExpr)}]"
    case tuple: STuple => q"Tuple(..${tuple.exprs.map(genExpr)})"
    case bi: SBodyItem => genBodyItem(bi)
    case m @ SMatch(sel, cs, tpe) =>
      val expr = genExpr(sel)
      val cases = cs.map{ case SCase(p, g, b, _) =>
        val pat = genPattern(p)
        val guard = genExpr(g)
        val body = genExpr(b)
        cq"$pat if $guard => $body"
      }
      q"$expr match { case ..$cases } "
    case unknown => throw new NotImplementedError(s"genExpr($unknown)")
  }

  def genPattern(pat: SPattern)(implicit ctx: GenCtx): Tree = pat match {
    case SWildcardPattern() => Bind(nme.WILDCARD, EmptyTree)
    case SApplyPattern(fun, pats) => Apply(genExpr(fun), pats.map(genPattern(_)))
    case STypedPattern(tpe) => Typed(Ident(termNames.WILDCARD), genTypeExpr(tpe))
    case SBindPattern(name, expr) => Bind(TermName(name), genPattern(expr))
    case SLiteralPattern(SConst(c, _)) => Literal(Constant(c))
    case SStableIdPattern(SIdent(id, _)) => Ident(id)
    case SSelPattern(qual, name) => Select(genExpr(qual), name)
    case SAltPattern(alts) => Alternative(alts.map(genPattern(_)))
    case _ => throw new NotImplementedError(s"genPattern($pat)")
  }

  def genBodyItem(item: SBodyItem)(implicit ctx: GenCtx): Tree = item match {
    case m: SMethodDef =>
      if (m.isTypeDesc) genMethod(m)(ctx = ctx.copy(toRep = false))
      else genMethod(m)
    case v: SValDef => genVal(v)
    case i: SImportStat => genImport(i)
    case t: STpeDef => genTypeDef(t)
    case o: SObjectDef => genObject(o)
    case tr: STraitDef => genTrait(tr)
    case c: SClassDef => genClass(c)
    case unknown => throw new NotImplementedError(s"genBodyItem($unknown)")
  }

  def genBody(body: List[SBodyItem])(implicit ctx: GenCtx): List[Tree] = body.map(genBodyItem)

  def genMethod(m: SMethodDef)(implicit ctx: GenCtx): Tree = {
    val tname = TermName(m.name)
    val impFlag = if (m.isImplicit) Flag.IMPLICIT else NoFlags
    val overFlag = if (m.isOverride) Flag.OVERRIDE else NoFlags
    val flags = Flag.PARAM | impFlag | overFlag
    val mods = Modifiers(flags, tpnme.EMPTY, genAnnotations(m.annotations))
    val tpt = m.tpeRes match {
      case Some(tpeRes) => if (!ctx.isVirtualized && ctx.toRep) repTypeExpr(tpeRes) else genTypeExpr(tpeRes)
      case None => EmptyTree
    }
    val paramss = genMethodArgs(m.argSections).filter(!_.isEmpty)
    val exprs = m.body match {
      case Some(expr) => genExpr(expr)
      case None => EmptyTree
    }
    val tparams = genTypeArgs(m.tpeArgs)

    q"$mods def $tname[..$tparams](...$paramss): $tpt = $exprs"
  }

  def genMethodArg(arg: SMethodArg)
      (implicit ctx: GenCtx): Tree = {
    val tname = TermName(arg.name)

    val overFlag = if (arg.overFlag) Flag.OVERRIDE else NoFlags
    val impFlag = if (arg.impFlag) Flag.IMPLICIT else NoFlags
    val flags = overFlag | impFlag
    val mods = Modifiers(flags, tpnme.EMPTY, genAnnotations(arg.annotations))
    if (ctx.isVirtualized) {
      arg.tpe match {
        case RepeatedArgType(targ) =>
          val t = genTypeExpr(targ)
          val repeated = q"def m(x: ${t}*): Unit".asInstanceOf[DefDef].vparamss(0)(0).tpt
          q"$mods val $tname: $repeated"
        case _ =>
          val tpt = genTypeExpr(arg.tpe)
          q"$mods val $tname: $tpt"
      }
    }
    else {
      arg.tpe match {
        case RepeatedArgType(targ) =>
          val t = repTypeExpr(targ)
          val repeated = q"def m(x: ${t}*): Unit".asInstanceOf[DefDef].vparamss(0)(0).tpt
          q"$mods val $tname: $repeated"
        case _ =>
          val tpt = if (arg.isTypeDesc) genTypeExpr(arg.tpe) else repTypeExpr(arg.tpe)
          q"$mods val $tname: $tpt"
      }
    }
  }

  def genMethodArgs(argSections: List[SMethodArgs])
      (implicit ctx: GenCtx): List[List[Tree]] = {
    argSections.map(_.args.map(genMethodArg))
  }

  def genVal(v: SValDef)(implicit ctx: GenCtx): Tree = {
    val impFlag = if (v.isImplicit) Flag.IMPLICIT else NoFlags
    val lazyFlag = if (v.isLazy) Flag.LAZY else NoFlags
    val mods = Modifiers(impFlag | lazyFlag)
    val tname = TermName(v.name)
    val tpt =v.tpe match {
      case Some(tpe) => if(ctx.isVirtualized) genTypeExpr(tpe) else repTypeExpr(tpe)
      case None => TypeTree()
    }
    val expr = genExpr(v.expr)

    q"$mods val $tname: $tpt = $expr"
  }

  def genImport(imp: SImportStat)(implicit ctx: GenCtx): Tree = {
    val impParts = imp.name.split('.').toList
    val refs = genRefs(impParts.init)
    val sels = impParts.last match {
      case "_" => List(Ident(termNames.WILDCARD))
      case bindName => List(Bind(TermName(bindName), Ident(termNames.WILDCARD)))
    }

    q"import $refs.{..$sels}"
  }

  def genTypeDef(t: STpeDef)(implicit ctx: GenCtx): Tree = {
    val tpname = TypeName(t.name)
    val tpt = genTypeExpr(t.tpe)
    val tparams = genTypeArgs(t.tpeArgs)

    q"type $tpname[..$tparams] = $tpt"
  }

  def genTypeArgs(tpeArgs: STpeArgs)
      (implicit ctx: GenCtx): List[TypeDef] = tpeArgs.map(genTypeArg)

  def genTypeArg(arg: STpeArg)(implicit ctx: GenCtx): TypeDef = {
    val tpname = TypeName(arg.name)
    val tparams = arg.tparams.map(genTypeArg)
    val mods = Modifiers(Flag.PARAM, tpnme.EMPTY, arg.annotations.map(genAnnotation(_)))
    val tpt = arg.bound match {
      case Some(tpe) => TypeBoundsTree(TypeTree(), genTypeExpr(tpe))
      case None => TypeTree()
    }

    q"$mods type $tpname[..$tparams] = $tpt"
  }

  def genObject(o: SObjectDef)(implicit ctx: GenCtx): Tree = {
    val tname = TermName(o.name)
    val parents = genParents(o.ancestors)
    val body = genBody(o.body)

    q"object $tname extends ..$parents { ..$body }"
  }

  def genRefs(refs: List[String])(implicit ctx: GenCtx): Tree = {
    if (refs.length == 1)
      Ident(TermName(refs.head))
    else
      q"${genRefs(refs.init)}.${TermName(refs.last)}"
  }

  def genTypeRefs(refs: List[String])(implicit ctx: GenCtx): Tree = {
    if (refs.length == 1)
      tq"${TypeName(refs.head)}"
    else
      tq"${genRefs(refs.init)}.${TypeName(refs.last)}"
  }

  def genTypeName(typeName: String)(implicit ctx: GenCtx): Tree = typeName match {
    case PackageAndName(packageNames, typeName) =>
      genTypeRefs(packageNames :+ typeName)
    case simpleName => genTypeRefs(List(simpleName))
  }

  def genTypeByName(name: String)(implicit ctx: GenCtx) = tq"${TypeName(name)}"

  def repTypeExpr(tpeExpr: STpeExpr)(implicit ctx: GenCtx) = tpeExpr match {
    case STpePrimitive(name: String, _) => tq"Ref[${TypeName(name)}]"
    case STraitCall(name: String, args: List[STpeExpr]) =>
      val targs = args.map(genTypeExpr)
      val appliedType = tq"${TypeName(name)}[..$targs]"
      if (ctx.context.typeClasses.contains(name))
        appliedType
      else
        tq"Ref[$appliedType]"
    case STpeTuple(_) => tq"Ref[${genTypeExpr(tpeExpr)}]"
    case STpeFunc(_, _) => tq"Ref[${genTypeExpr(tpeExpr)}]"
    case STpeThis(fullName, _) => tq"Ref[${TypeName(fullName)}.this.type]"
    case unknown =>
      throw new NotImplementedError(s"repTypeExp($unknown)")
  }

  def genClassArg(arg: SClassArg)(implicit ctx: GenCtx): Tree = {
    val tname = TermName(arg.name)
    val tpt = if (ctx.isVirtualized || arg.isTypeDesc) genTypeExpr(arg.tpe) else repTypeExpr(arg.tpe)
    val valFlag = if (arg.valFlag) Flag.PARAMACCESSOR else NoFlags
    val overFlag = if (arg.overFlag) Flag.OVERRIDE else NoFlags
    val impFlag = if (arg.impFlag) Flag.IMPLICIT else NoFlags
    val flags = Flag.PARAM | valFlag | overFlag | impFlag
    val mods = Modifiers(flags, tpnme.EMPTY, genAnnotations(arg.annotations))

    q"$mods val $tname: $tpt"
  }

  def genClassArgs(args: SClassArgs, implicitArgs: SClassArgs)
      (implicit ctx: GenCtx): List[List[Tree]] = {
    val repArgs = args.args.map(genClassArg)
    val repImplArgs = implicitArgs.args.map(genClassArg)
    val repClassArgs = List[List[Tree]](repArgs, repImplArgs)

    repClassArgs.filterNot(_.isEmpty)
  }

  def genConstr(constr: SConstr)(implicit ctx: GenCtx): Tree = {
    val argsTree = constr.args.map(genExpr)
    val constrName = constr.name.split('.').last.split('[').head

    Apply(Ident(TermName(constrName)), argsTree)
  }

  def genFunc(func: SFunc)(implicit ctx: GenCtx): Tree = {
    if (func.params.length == 1) {
      if (ctx.isVirtualized)
        q"{ (${genVal(func.params.head) }) => ${genExpr(func.res) } }"
      else
        q"fun { (${genVal(func.params.head) }) => ${genExpr(func.res) } }"
    } else {
      val t: List[STpeExpr] = func.params.map(_.tpe.getOrElse(STpeEmpty()))
      val tAst = genTuples(t)
      val (_, vals) = func.params.foldLeft((1, List[Tree]())) { (acc, param) =>
        val tres = param.tpe match {
          case Some(tpe) => if (ctx.isVirtualized) genTypeExpr(tpe) else repTypeExpr(tpe)
          case None => TypeTree()
        }
        val inval = q"in.${TermName("_" + acc._1.toString())}"
        (acc._1 + 1, q"val ${TermName(param.name)}: $tres = $inval" :: acc._2)
      }
      val body = q"{ ..${vals.reverse}; ${genExpr(func.res)} }"
      q"fun { (in: Ref[$tAst]) => $body }"
    }
  }

  def genTrait(tr: STraitDef)(implicit ctx: GenCtx): Tree = {
    val entityName = TypeName(tr.name)
    val entitySelf = genSelf(tr.selfType)
    val repStats = genBody(tr.body)
    val entityParents = genParents(tr.ancestors)
    val tparams = tr.tpeArgs.map(genTypeArg)
    val mods = Modifiers(NoFlags, tpnme.EMPTY, genAnnotations(tr.annotations))
    val res = q"$mods trait $entityName[..$tparams] extends ..$entityParents { $entitySelf => ..$repStats }"

    res
  }

  def genClass(c: SClassDef)(implicit ctx: GenCtx): Tree = {
    val className = TypeName(c.name)
    val classSelf = genSelf(c.selfType)
    val parents = genParents(c.ancestors)
    val repStats = genBody(c.body)
    val repparamss = genClassArgs(c.args, c.implicitArgs)
    val flags = if (c.isAbstract) Flag.ABSTRACT else NoFlags
    val mods = Modifiers(flags, tpnme.EMPTY, genAnnotations(c.annotations))
    val tparams = c.tpeArgs.map(genTypeArg)
    val res = q"""
            $mods class $className[..$tparams] (...$repparamss)
            extends ..$parents
            { $classSelf => ..$repStats }
            """
    res
  }

  def genConcreteClasses(classes: List[SClassDef])(implicit ctx: GenCtx): List[Tree] = {
    classes.map{clazz => genClass(clazz.copy(isAbstract = true))}
  }

  def genAnnotations(annotations: List[SAnnotation])(implicit ctx: GenCtx): List[Tree] = {
    filterInternalAnnot(annotations).map(genAnnotation)
  }

  def genAnnotation(annot: SAnnotation)(implicit ctx: GenCtx): Tree = {
    val args = annot.args.map(a => genExpr(a)(ctx.copy(toRep = false)))
    val annotClass = if (annot.tpeArgs.isEmpty)
      Ident(annot.annotationClass)
    else
      genTypeExpr(STraitCall(annot.annotationClass, annot.tpeArgs))
    Apply(Select(New(annotClass), nme.CONSTRUCTOR), args)
  }
}

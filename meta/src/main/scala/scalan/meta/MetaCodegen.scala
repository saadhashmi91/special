package scalan.meta

import scalan.util.PrintExtensions._

import scala.collection.mutable.ArrayBuffer
import scalan.util.{CollectionUtil, StringUtil, ScalaNameUtil, PrintExtensions}
import CollectionUtil._
import StringUtil.StringUtilExtensions
import scalan.meta.ScalanAst._
import scalan.meta.ScalanAstExtensions._
import scalan.meta.ScalanAstUtils.classArgsAsSeenFromAncestors

class MetaCodegen {

  def dataType(ts: List[STpeExpr]): String = ts match {
    case Nil => "Unit"
    case t :: Nil => t.toString
    case t :: ts => s"($t, ${dataType(ts)})"
  }

  def pairify(fs: List[String]): String = fs match {
    case Nil => "unit"
    case f :: Nil => f
    case f :: fs => s"Pair($f, ${pairify(fs)})"
  }

  def zeroSExpr(entity: SEntityDef)(t: STpeExpr): String = t match {
    case STpePrimitive(_, defaultValueString) => defaultValueString
    case STraitCall(name, args) if entity.tpeArgs.exists(a => a.name == name && a.isHighKind) =>
      val arg = args(0)
      arg match {
        case STraitCall(name2, _) if entity.tpeArgs.exists(a => a.name == name2) =>
          s"c$name.lift(${args.rep(a => s"e$a")}).defaultRepValue"
        case _ =>
          s"c$name.lift(${args.rep(a => s"element[$a]")}).defaultRepValue"
      }
    case tc@STraitCall(name, args) => {
        s"element[$t].defaultRepValue"
    }
    case STpeTuple(items) => pairify(items.map(zeroSExpr(entity)))
    case STpeFunc(domain, range) => s"""constFun[$domain, $range](${zeroSExpr(entity)(range)})"""
    case t => throw new IllegalArgumentException(s"Can't generate zero value for $t")
  }

  def entityElemMethodName(name: String) = StringUtil.lowerCaseFirst(name) + "Element"

  def tpeToElemStr(t: STpeExpr, env: List[STpeArg])(implicit ctx: AstContextBase): String = t match {
    case STpePrimitive(name,_) => name + "Element"
    case STpeTuple(List(a, b)) => s"pairElement(${tpeToElemStr(a, env)},${tpeToElemStr(b, env)})"
    case STpeFunc(a, b) => s"funcElement(${tpeToElemStr(a, env)},${tpeToElemStr(b, env)})"
    case STraitCall("$bar", List(a,b)) => s"sumElement(${tpeToElemStr(a, env)},${tpeToElemStr(b, env)})"
    case STraitCall(name, Nil) if STpePrimitives.contains(name) => name + "Element"
    case STraitCall(name, Nil) if ctx.getKind(name) > 0 =>
      ctx.getKind(name) match {
        case 1 => s"container[$name]"
        case 2 => s"container2[$name]"
        case k => sys.error(s"Cannot tpeToElement($t, $env): Kind $k of $name is not supported.")
      }
    case STraitCall(name, args) if env.exists(_.name == name) =>
      val a = env.find(_.name == name).get
      if (!a.isHighKind)
        s"element[$t]"
      else
      if (args.isEmpty)
        s"container[$t]"
      else
        s"element[$t]"
    case STraitCall(name, args) =>
      val method = entityElemMethodName(name)
      val argsStr = args.rep(tpeToElemStr(_, env))
      method + args.nonEmpty.opt(s"($argsStr)")
    case _ => sys.error(s"Don't know how to construct Elem string for type $t")
  }

  class EntityTypeBuilder(entity: SEntityDef) {
    private val _args: ArrayBuffer[String] = ArrayBuffer.empty
    private val _forSomeTypes: ArrayBuffer[String] = ArrayBuffer.empty

    {
      var iHKind = 1
      for (a <- entity.tpeArgs) {
        if (a.isHighKind) {
          val tyName = "F" + iHKind
          _args += tyName
          _forSomeTypes += s"type $tyName[_]"
          iHKind += 1
        }
        else {
          _args += "_"
        }
      }
    }

    def elemTypeName = {
      s"${entity.name}Elem[${_args.rep()}]${_forSomeTypes.opt(ts => s"forSome {${ts.rep(_.toString, ";")}}") }"
    }
  }

  def emitImplicitElemDeclByTpePath(prefixExpr: String, tailPath: STpePath) = {
    def emit(prefix: String, tailPath: STpePath, typed: Boolean): String = tailPath match {
      case SNilPath => prefix
      case STuplePath(_, i, t) => i match {
        case 0 =>
          if (typed)
            emit(s"$prefix.eFst", t, typed)
          else
            emit(s"$prefix.asInstanceOf[PairElem[_,_]].eFst", t, typed)
        case 1 =>
          if (typed)
            emit(s"$prefix.eSnd", t, typed)
          else
            emit(s"$prefix.asInstanceOf[PairElem[_,_]].eSnd", t, typed)
        case _ =>
          sys.error(s"Unsupported tuple type ($prefix, $tailPath)")
      }
      case SDomPath(_, t) =>
        if (typed)
          emit(s"$prefix.eDom", t, typed)
        else
          emit(s"$prefix.asInstanceOf[FuncElem[_,_]].eDom", t, typed)
      case SRangePath(_, t) =>
        if (typed)
          emit(s"$prefix.eRange", t, typed)
        else
          emit(s"$prefix.asInstanceOf[FuncElem[_,_]].eRange", t, typed)
      case SThunkPath(_, t) =>
        if (typed)
          emit(s"$prefix.eItem", t, typed)
        else
          emit(s"$prefix.asInstanceOf[ThunkElem[_]].eItem", t, typed)
      case SStructPath(_, fn, t) =>
        emit(s"""$prefix.asInstanceOf[StructElem[_]]("$fn")""", t, false)
      case SEntityPath(STraitCall(name, args), e, tyArg, t) =>
        val argIndex = e.tpeArgs.indexByName(tyArg.name)
        val argTy = args(argIndex)
        val descName = tyArg.descName
        emit(s"""$prefix.typeArgs("${tyArg.name}")._1.asInstanceOf[$descName[$argTy]]""", t, true)
      case _ => sys.error(s"emit($tailPath)")
    }
    emit(prefixExpr, tailPath, true)
  }

  /** Build element extraction expression for each type argument.
    * @param m the module we are working in
    * @param dataArgs data arguments which we can use to extract elements from
    * @param tpeArgs the type arguments for which elements should be extracted
    * @return a list, where for each type argument either Some(element extraction expression) or None is returned
    */
  def extractImplicitElems(
        m: SUnitDef, dataArgs: List[SMethodOrClassArg],
        tpeArgs: List[STpeArg],
        argSubst: Map[String, String] = Map(),
        extractFromEntity: Boolean = true): List[(STpeArg, Option[String])] =
  {
    def subst(arg: String) = argSubst.getOrElse(arg, arg)

    tpeArgs.map { ta =>
      val paths = for {
          da <- dataArgs.iterator
          argTpe <- da.tpe.unRep(m, m.isVirtualized)
          path <- STpePath.find(argTpe, ta.name)(m.context)
        } yield (da, path)
      val expr = paths.find(_ => true).map {
        case (da, SEntityPath(_, e, tyArg, tail)) if extractFromEntity =>
          val prefix = s"${subst(da.name) }.${tyArg.classOrMethodArgName() }"
          emitImplicitElemDeclByTpePath(prefix, tail)
        case (da, path) =>
          val prefix = s"${subst(da.name) }.elem"
          emitImplicitElemDeclByTpePath(prefix, path)
      }
      (ta, expr)
    }
  }

  def methodExtractorsString(module: SUnitDef, config: UnitConfig, e: SEntityDef) = {
    implicit val ctx = module.context
    def methodExtractorsString1(e: SEntityDef, isCompanion: Boolean) = {
      val methods = e.body.collect { case m: SMethodDef => optimizeMethodImplicits(m) }
      val overloadIdsByName = collection.mutable.Map.empty[String, Set[Option[String]]].withDefaultValue(Set())
      methods.foreach { m =>
        val methodName = m.name
        val overloadId = m.overloadId
        val overloadIds = overloadIdsByName(methodName)
        if (overloadIds.contains(overloadId)) {
          sys.error(s"Duplicate overload id for method ${e.name}.$methodName: ${overloadId}. Use scalan.OverloadId annotation with different values for each overload (one overload can be unannotated).")
        } else {
          overloadIdsByName(methodName) = overloadIds + overloadId
        }
      }

      def reasonToSkipMethod(m: SMethodDef): Option[String] = {
        (m.explicitArgs.filter { arg => arg.tpe.isInstanceOf[STpeFunc] && config.isVirtualized } match {
          case Seq() => None
          case nonEmpty => Some(s"Method has function arguments ${nonEmpty.rep(_.name)}")
        }).orElse {
          m.name match {
            case "toString" | "hashCode" if m.allArgs.isEmpty =>
              Some(s"Overrides Object method ${m.name}")
            case "equals" | "canEqual" if m.allArgs.length == 1 =>
              Some(s"Overrides Object method ${m.name}")
            case _ => None
          }
        }.orElse {
          m.tpeRes.filter(!_.isRep(module, config.isVirtualized)).map {
            returnTpe => s"Method's return type $returnTpe is not a Ref"
          }
        }
//        .orElse {
//          m.allArgs
//            .find(a => a.tpe match { case RepeatedArgType(t) => true case _ => false })
//            .map(a => s"Method has repeated argument ${a.name}")
//        }
      }

      def methodExtractor(m: SMethodDef): String = {
        reasonToSkipMethod(m) match {
          case Some(reason) =>
            s"    // WARNING: Cannot generate matcher for method `${m.name}`: $reason"
          case _ =>
            // DummyImplicit and Overloaded* are ignored, since
            // their values are never useful
            val methodArgs = m.allArgs.takeWhile { arg =>
              arg.tpe match {
                case STraitCall(name, _) =>
                  !(name == "DummyImplicit" || name.startsWith("Overloaded"))
                case _ => true
              }
            }
            val typeVars = (e.tpeArgs ++ m.tpeArgs).map(_.declaration).toSet
            val returnType = {
              val receiverType = s"Ref[${e.name + e.tpeArgs.asTypeParams(_.name)}]"
              val argTypes = methodArgs.map { arg =>
                arg.tpe match {
                  case RepeatedArgType(t) =>
                    if (config.isVirtualized)
                      s"Seq[$t]"
                    else
                      s"Seq[Ref[$t]]"
                  case _ =>
                    if (config.isVirtualized || arg.isTypeDesc)
                      arg.tpe.toString
                    else
                      s"Ref[${arg.tpe}]"
                }
              }
              val receiverAndArgTypes = ((if (isCompanion) Nil else List(receiverType)) ++ argTypes) match {
                case Seq() => "Unit"
                case Seq(single) => single
                case many => many.mkString("(", ", ", ")")
              }
              receiverAndArgTypes + typeVars.opt(typeVars => s" forSome {${typeVars.map("type " + _).mkString("; ")}}")
            }
            val overloadId = m.overloadId
            val cleanedMethodName = ScalaNameUtil.cleanScalaName(m.name)
            val matcherName = {
              overloadId match {
                case None => cleanedMethodName
                case Some(id) =>
                  // make a legal identifier containing overload id
                  if (ScalaNameUtil.isOpName(cleanedMethodName)) {
                    id + "_" + cleanedMethodName
                  } else {
                    cleanedMethodName + "_" + id
                  }
              }
            }

            val matchResult = ((if (isCompanion) Nil else List("receiver")) ++ methodArgs.indices.map(i => s"args($i)")) match {
              case Seq() => "()"
              case Seq(single) => single
              case many => many.mkString("(", ", ", ")")
            }

            val methodPattern = {
              // _* is for dummy implicit arguments
              val methodArgsPattern = if (methodArgs.isEmpty) "_" else "args"
              val typeArgsNum =
                if (isCompanion) {
                  0
                } else if (e.isInstanceOf[STraitDef]) {
                  e.tpeArgs.length + 1
                } else {
                  e.tpeArgs.length
                }
              val traitElem = s"${e.name}Elem${Seq.fill(typeArgsNum)("_").asTypeParams()}"
              val annotationCheck =
                if (overloadIdsByName(m.name).size == 1) {
                  // nothing to check if method isn't overloaded
                  ""
                } else {
                  overloadId match {
                    case None =>
                      " && method.getAnnotation(classOf[scalan.OverloadId]) == null"
                    case Some(id) =>
                      s""" && { val ann = method.getAnnotation(classOf[scalan.OverloadId]); ann != null && ann.value == "$id" }"""
                  }
                }

              val elemCheck = if (isCompanion) {
                s"receiver.elem == $traitElem"
              } else if (e.hasHighKindTpeArg) {
                // same as isInstanceOf[$traitElem], but that won't compile
                s"(receiver.elem.asInstanceOf[Elem[_]] match { case _: $traitElem => true; case _ => false })"
              } else {
                s"receiver.elem.isInstanceOf[$traitElem]"
              }
              s"""MethodCall(receiver, method, $methodArgsPattern, _) if method.getName == "${m.name}" && $elemCheck$annotationCheck"""
            }
            // TODO we can use name-based extractor to improve performance when we switch to Scala 2.11
            // See http://hseeberger.github.io/blog/2013/10/04/name-based-extractors-in-scala-2-dot-11/

            s"""    object $matcherName {
              |      def unapply(d: Def[_]): Nullable[$returnType] = d match {
              |        case $methodPattern =>
              |          val res = $matchResult
              |          Nullable(res).asInstanceOf[Nullable[$returnType]]
              |        case _ => Nullable.None
              |      }
              |      def unapply(exp: Sym): Nullable[$returnType] = unapply(exp.node)
              |    }""".stripAndTrim
        }
      }

      s"""  object ${e.name}Methods {
        |${methods.filterNot(_.isTypeDesc).map(methodExtractor).mkString("\n\n")}
        |  }""".stripMargin
    }

    s"""${methodExtractorsString1(e, false)}
      |
      |${e.companion.opt(methodExtractorsString1(_, true))}""".stripMargin
  }

  // methods to extract elements from data arguments
  class ElemExtractionBuilder(
      module: SUnitDef, entity: SEntityDef,
      argSubst: Map[String, String], extractFromEntity: Boolean = true)(implicit ctx: AstContextBase) {
    val extractionExprs: List[Option[String]] =
       extractImplicitElems(module, entity.args.args, entity.tpeArgs, argSubst, extractFromEntity).map(_._2)
    val tyArgSubst = classArgsAsSeenFromAncestors(entity).map { case (_, (e,a)) => a }
    val extractableArgs: Map[String,(STpeArg, String)] =
      tyArgSubst.zip(extractionExprs)
        .collect { case (arg, Some(expr)) => (arg.name, (arg, expr)) }.toMap
    def emitExtractableImplicits(inClassBody: Boolean): String = {
      extractableArgs.map { case (tyArgName, (arg, expr)) =>
        val name = arg.classOrMethodArgName(tyArgName)
        val isInheritedDefinition = entity.isConcreteInAncestors(name)(module.context)
        val over = if (inClassBody && isInheritedDefinition) " override" else ""
        val declaareLazy = if (inClassBody) " lazy" else ""
        s"implicit$over$declaareLazy val $name = $expr"
      }.mkString(";\n")
    }

    def isExtractable(a: SClassArg): Boolean = a.tpe match {
      case STraitCall("Elem", List(STraitCall(tyArgName, Nil))) =>
        extractableArgs.contains(tyArgName)
      case STraitCall("Cont", List(STraitCall(tyArgName, Nil))) =>
        extractableArgs.contains(tyArgName)
      case _ => false
    }
  }

  abstract class TemplateData(val unit: SUnitDef, val entity: SEntityDef) {
    implicit val context = unit.context
    val name = entity.name
    val tpeArgs = entity.tpeArgs
    val tpeArgNames = tpeArgs.names
    val tpeArgsDecl = tpeArgs.declString
    val tpeArgsUse = tpeArgs.useString
    val typeDecl = name + tpeArgsDecl
    def typeDecl(suffix: String) = name + suffix + tpeArgsDecl
    val typeUse = name + tpeArgsUse
    def typeUse(suffix: String) = name + suffix + tpeArgsUse
    val implicitArgs = entity match {
      case c: SClassDef => c.implicitArgs.args
      case t: STraitDef => t.getImplicitArgsForTpeArgs.args
    }
    def implicitArgsDecl(prefix: String = "", p: SClassArg => Boolean = _ => true) =
      implicitArgs.filter(p).opt(args => s"(implicit ${args.rep(a => s"$prefix${a.name}: ${a.tpe}")})")
    def implicitArgsDeclConcreteElem = {
      implicitArgs.opt(args => s"(implicit ${args.rep(a => {
                                 val isAbstract = entity.isAbstractInAncestors(a.name)
                                 val isConcrete = entity.isConcreteInAncestors(a.name)
                                 s"${(isAbstract || isConcrete).opt("override ")}val ${a.name}: ${a.tpe}"
                                           })})")
    }
    val implicitArgsUse = implicitArgs.opt(args => s"(${args.rep(_.name)})")
    val implicitArgsOrParens = if (implicitArgs.nonEmpty) implicitArgsUse else "()"
    val firstAncestorType = entity.firstAncestorType

    def entityRepSynonym = STpeDef(unit.unitSym, "Ref" + name, tpeArgs, STraitCall("Ref", List(STraitCall(name, tpeArgs.map(_.toTraitCall)))))

    def isCont = tpeArgs.length == 1 && entity.hasAnnotation(ContainerTypeAnnotation)
    def isFunctor = tpeArgs.length == 1 && entity.hasAnnotation(FunctorTypeAnnotation)

    def boundedTpeArgString(withTags: Boolean = false) = tpeArgs.getBoundedTpeArgString(withTags)

    //TODO use getAvailableDescMethods
    val allDescs = entity.args.args ++ implicitArgs

    def emitTpeArgToDescPairs = {
      tpeArgs.flatMap { tpeArg =>
        val tyArgName = tpeArg.name
        val argOpt = allDescs.find { a =>
          a.tpe match {
            case TypeDescTpe(descName, STraitCall(`tyArgName`, _)) => true
            case _ => false
          }
        }
        argOpt.map { arg =>
          s"${StringUtil.quote(tyArgName)} -> (${arg.name} -> scalan.util.${tpeArg.variance})"
        }
      }.rep()
    }

    def companionName = name + "Companion"
    def companionCtorName = name + "CompanionCtor"

    def extractionBuilder(prefix: String): ElemExtractionBuilder = {
      val s = entity.args.args.map { a => a.name -> (prefix + a.name) }.toMap
      extractionBuilder(s)
    }

    def extractionBuilder(
          argSubst: Map[String, String] = Map(),
          extractFromEntity: Boolean = true): ElemExtractionBuilder =
      new ElemExtractionBuilder(unit, entity, argSubst, extractFromEntity)
  }

  case class EntityTemplateData(m: SUnitDef, t: SEntityDef) extends TemplateData(m, t) {
    def elemTypeUse(toType: String = typeUse) = s"${name}Elem[${PrintExtensions.join(tpeArgNames, toType)}]"
    val typesWithElems = boundedTpeArgString(false)
    def optimizeImplicits(): EntityTemplateData = t match {
      case t: STraitDef =>
        this.copy(t = optimizeTraitImplicits(t))
      case c: SClassDef =>
        this.copy(t = optimizeClassImplicits(c))
    }
  }

  case class ConcreteClassTemplateData(m: SUnitDef, c: SClassDef) extends TemplateData(m, c) {
    val elemTypeUse = name + "Elem" + tpeArgsUse
    def optimizeImplicits(): ConcreteClassTemplateData = {
      this.copy(c = optimizeClassImplicits(c))
    }
    val abstractDescriptors: List[SEntityMember] = c.collectVisibleMembers.filter { m => m.item.isAbstract && m.item.isTypeDesc }
    val nonExtractableDescriptors: List[(SEntityMember, Option[String])] = {
      val b = extractionBuilder()
      val res = abstractDescriptors.filterMap { m =>
        def isSameResultType(tyArg: STpeArg) = m.item.tpeRes.get match {
          case TypeDescTpe(_, targ) => targ == tyArg.toTraitCall
          case _ => false
        }
        val optExtractionExpr = b.extractableArgs.collectFirst {
          case (n, (tyArg, s)) if isSameResultType(tyArg) =>
            tyArg.classOrMethodArgName(n)
        }
        optExtractionExpr match {
          case Some(itemName) =>
            if (itemName == m.item.name)
              None  // means filter out this m
            else
              Some((m, Some(itemName))) // this entity member CAN be computed from extractable arg
          case None =>
            Some((m, None)) // this entity member SHOULD be defined elsewhere
        }
      }
      res
    }
    val elemDefs: String = nonExtractableDescriptors.rep({
      case (m, None) =>
        val tc @ STraitCall(descName, List(tyStr)) = m.item.tpeRes.get
        s"override lazy val ${m.item.name}: $descName[$tyStr] = implicitly[$descName[$tyStr]]"
      case (m, Some(extractedName)) =>
        val STraitCall(descName, List(tyStr)) = m.item.tpeRes.get
        s"override lazy val ${m.item.name}: $descName[$tyStr] = $extractedName"
    }, "\n|").stripMargin
  }

}

object ScalanCodegen extends MetaCodegen

package scalan.meta.scalanizer

import scala.tools.nsc.Global
import scalan.meta.ScalanAst.{SValDef, STpeExpr, STpeFunc, STpeEmpty, SModuleDef, STpeTuple, KernelType, SFunc, WrapperDescr}

/** The object contains the current state and temporary data of the Scalanizer. */
trait ScalanizerState[G <: Global] {
  import scala.collection.mutable.Map
  val scalanizer: Scalanizer[G]
  import scalanizer._
  import global._

  /** Mapping of module name to its extensions that should be generated by the plugin.
    * For example: Segments -> Set(SegmentsDsl, SegmentsDslStd, SegmentsDslExp) */
  val subcakesOfModule: Map[String, Set[String]]

  /** Mapping between modules and another modules used by them.
    * For example "Vecs" -> List("NumMonoids", "Cols", "LinearAlgebra") */
  val dependenceOfModule: Map[String, List[String]]

  /** Mapping of module name to the package where it is defined.
    * For example "Cols" -> "scalanizer.collections" */
  val packageOfModule: Map[String, String]

  /** Mapping of external type names to their wrappers. */
  val wrappers: Map[String, WrapperDescr]

  def updateWrapper(typeName: String, descr: WrapperDescr) = {
    wrappers(typeName) = descr
  }
  /** Names of external types. They must be read only after the WrapFrontend phase. */
  def externalTypes = wrappers.keySet

  val modules: Map[String, SModuleDef]

  def getModule(packageName: String, unitName: String): SModuleDef = {
    val key = s"$packageName.$unitName"
    modules(key)
  }

  def addModule(unitName: String, module: SModuleDef) = {
    val key = s"${module.packageName}.$unitName"
    modules(key) = module
  }
}

package scalan.plugin

import scala.tools.nsc.Global
import scalan.meta.ScalanAst.WrapperDescr
import scalan.meta.scalanizer.{Scalanizer, ScalanizerState}

/** The object contains the current state and temporary data of the plugin. */
class ScalanizerPluginState[G <: Global](val scalanizer: Scalanizer[G]) extends ScalanizerState[G] {
  import scala.collection.mutable.Map
  /** Mapping of module name to its extensions that should be generated by the plugin.
    * For example: Segments -> Set(SegmentsDsl, SegmentsDslStd, SegmentsDslExp) */
  val subcakesOfModule = Map[String, Set[String]](
    "WPredefs" -> Set("WPredefsDsl", "WPredefsDslStd", "WPredefsDslExp"),
    "WGenIterables" -> Set("WGenIterablesDsl", "WGenIterablesDslStd", "WGenIterablesDslExp"),
    "WCanBuildFroms" -> Set("WCanBuildFromsDsl", "WCanBuildFromsDslStd", "WCanBuildFromsDslExp"),
    "WArrays" -> Set("WArraysDsl", "WArraysDslStd", "WArraysDslExp"),
    "WArrayOpss" -> Set("WArrayOpssDsl", "WArrayOpssDslStd", "WArrayOpssDslExp"),
    "WWrappedArrays" -> Set("WWrappedArraysDsl", "WWrappedArraysDslStd", "WWrappedArraysDslExp"),
    "WNums" -> Set("WNumsDsl", "WNumsDslStd", "WNumsDslExp"),
    "WDoubleNums" -> Set("WDoubleNumsDsl", "WDoubleNumsDslStd", "WDoubleNumsDslExp"),
    "WNumMonoids" -> Set("WNumMonoidsDsl", "WNumMonoidsDslStd", "WNumMonoidsDslExp"),
    "WPlusMonoids" -> Set("WPlusMonoidsDsl", "WPlusMonoidsDslStd", "WPlusMonoidsDslExp"),
    "WCols" -> Set("WColsDsl", "WColsDslStd", "WColsDslExp"),
    "WVecs" -> Set("WVecsDsl", "WVecsDslStd", "WVecsDslExp"),
    "WDenseVecs" -> Set("WDenseVecsDsl", "WDenseVecsDslStd", "WDenseVecsDslExp"),
    "WMatrs" -> Set("WMatrsDsl", "WMatrsDslStd", "WMatrsDslExp"),
    "WDenseMatrs" -> Set("WDenseMatrsDsl", "WDenseMatrsDslStd", "WDenseMatrsDslExp"),
    "WMatrOps" -> Set("WMatrOpsDsl", "WMatrOpsDslStd", "WMatrOpsDslExp"),
    "WBaseMatrOps" -> Set("WBaseMatrOpsDsl", "WBaseMatrOpsDslStd", "WBaseMatrOpsDslExp")
  )

  scalanizer.snConfig.codegenConfig.entityFiles.foreach { file =>
    val fileNameParts = file.split('.')
    if (!fileNameParts.isEmpty) {
      val moduleName = fileNameParts.head
      subcakesOfModule(moduleName) = Set(moduleName + "Dsl", moduleName + "DslStd", moduleName + "DslExp")
    }
  }

  /** Mapping between modules and another modules used by them. */
  val dependenceOfModule = Map[String, List[String]](
    "Cols" -> List("LinearAlgebra"),
    "LinearAlgebra" -> List("NumMonoids", "Cols", "Vecs", "Matrs")
  )

  /** Mapping of module name to the package where it is defined. */
  val packageOfModule = Map[String, String](
    "Cols" -> "scalanizer.collections",
    "LinearAlgebra" -> "scalanizer.linalgebra"
  )

  /** Mapping of external type names to their wrappers. */
  val wrappers = Map[String, WrapperDescr]()
}

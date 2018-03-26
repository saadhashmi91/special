package scalan.meta.scalanizer

import scala.tools.nsc.Global
import scalan.meta.ScalanAst.{SUnitDef, SValDef, STpeExpr, STpeFunc, STpeEmpty, STpeTuple, KernelType, SFunc, WrapperDescr}
import scalan.meta.UnitConfig

/** The object contains the current state and temporary data of the Scalanizer. */
trait ScalanizerState[+G <: Global] {
  val scalanizer: Scalanizer[G]
  import scalanizer._
  import global._

  def updateWrapper(typeName: String, descr: WrapperDescr) = {
    scalanizer.context.updateWrapper(typeName, descr)
  }

  def hasWrapper(typeName: String) = scalanizer.context.hasWrapper(typeName)
  def getWrapper(typeName: String) = scalanizer.context.getWrapper(typeName)

  def forEachWrapper(action: ((String, WrapperDescr)) => Unit) = {
    scalanizer.context.forEachWrapper(action)
  }

  def transformWrappers(transformer: ((String, WrapperDescr)) => WrapperDescr) = {
    scalanizer.context.transformWrappers(transformer)
  }

  def getUnit(packageName: String, unitName: String): SUnitDef = {
    scalanizer.context.getUnit(packageName, unitName)
  }

  def addUnit(unit: SUnitDef, unitConf: UnitConfig) = {
    scalanizer.context.addUnitConfig(unitConf)
    scalanizer.context.addUnit(unit)
  }
}

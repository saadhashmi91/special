package scalan.it

import scalan.{BaseTests, TestContexts, ScalanEx}

/**
 * Base class for integration testing
 *
 * @param mkProgStd By-name parameter to construct the sequential version of [[Prog]]. Called only once. `???` may be passed
 *                  if the class doesn't use [[ItTestUtils.compareOutputWithStd]].
 * @tparam Prog Program type
 */
abstract class BaseItTests[Prog <: ScalanEx](mkProgStd: => Prog) extends BaseTests with ItTestUtils[Prog] {
  lazy val progStd = mkProgStd
}

abstract class BaseCtxItTests[Prog <: ScalanEx](mkProgStd: => Prog) extends BaseItTests[Prog](mkProgStd) with TestContexts
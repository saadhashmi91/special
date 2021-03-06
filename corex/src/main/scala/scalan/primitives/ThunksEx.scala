package scalan.primitives

import scalan.{ScalanEx, BaseEx}

trait ThunksEx extends BaseEx with Thunks { self: ScalanEx =>
  import IsoUR._

  case class ThunkView[A, B](source: Ref[Thunk[A]])(innerIso: Iso[A, B])
      extends View1[A, B, Thunk](thunkIso(innerIso)) {
  }

  override def unapplyViews[T](s: Ref[T]): Option[Unpacked[T]] = (s match {
    case Def(view: ThunkView[_,_]) =>
      Some((view.source, view.iso))
    case _ =>
      super.unapplyViews(s)
  }).asInstanceOf[Option[Unpacked[T]]]

  override def rewriteViews[T](d: Def[T]) = d match {
    case th @ ThunkDef(HasViews(srcRes, iso: Iso[a,b]), _) => {
      implicit val eA = iso.eFrom
      implicit val eB = iso.eTo
      val newTh = Thunk { iso.from(forceThunkDefByMirror(th.asInstanceOf[ThunkDef[b]])) }   // execute original th as part of new thunk
      ThunkView(newTh)(iso)
    }
    case ThunkForce(HasViews(srcTh, Def(iso: ThunkIso[a, b]))) => {
      val innerIso = iso.innerIso
      implicit val eA = innerIso.eFrom
      innerIso.to(srcTh.asInstanceOf[Ref[Thunk[a]]].force)
    }
    case _ => super.rewriteViews(d)
  }

}

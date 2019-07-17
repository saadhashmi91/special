/**
 * Author: Alexander Slesarenko
 * Date: 7/25/12
 */
package scalan.primitives

import scalan.OverloadHack._
import scalan.{Base, Scalan, AVHashMap}

trait Tuples extends Base { self: Scalan =>
  object Pair {
    def apply[A, B](a: Rep[A], b: Rep[B]) = zipPair[A, B]((a, b))
    def unapply[A, B](p: Rep[(A, B)]) = Some(unzipPair[A, B](p))
  }

  object IsPair {
    def unapply[A,B](s: Sym): Option[Rep[(A,B)]] = s.elem match {
      case pe: PairElem[_,_] => Some(s.asInstanceOf[Rep[(A,B)]])
      case _ => None
    }
  }

  implicit class ListOps[A, B](t: Rep[(A, B)]) {
    def head: Rep[A] = { val Pair(x, _) = t; x }
    def tail: Rep[B] = { val Pair(_, x) = t; x }
  }

  implicit class TupleOps2[A, B](t: Rep[(A, B)]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, x) = t; x }
  }

  implicit class TupleOps3[A,B,C](t: Rep[(A,(B,C))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, x)) = t; x }
  }

  implicit class TupleOps4[A,B,C,D](t: Rep[(A,(B,(C,D)))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, x))) = t; x }
  }

  implicit class TupleOps5[A,B,C,D,E](t: Rep[(A,(B,(C,(D,E))))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, Pair(x, _)))) = t; x }
    def _5: Rep[E] = { val Pair(_, Pair(_, Pair(_, Pair(_, x)))) = t; x }
  }

  implicit class TupleOps6[A,B,C,D,E,F](t: Rep[(A,(B,(C,(D,(E,F)))))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, Pair(x, _)))) = t; x }
    def _5: Rep[E] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))) = t; x }
    def _6: Rep[F] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, x))))) = t; x }
  }

  implicit class TupleOps7[A,B,C,D,E,F,G](t: Rep[(A,(B,(C,(D,(E,(F,G))))))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, Pair(x, _)))) = t; x }
    def _5: Rep[E] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))) = t; x }
    def _6: Rep[F] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))) = t; x }
    def _7: Rep[G] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, x)))))) = t; x }
  }

  implicit class TupleOps8[A,B,C,D,E,F,G,H](t: Rep[(A,(B,(C,(D,(E,(F,(G,H)))))))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, Pair(x, _)))) = t; x }
    def _5: Rep[E] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))) = t; x }
    def _6: Rep[F] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))) = t; x }
    def _7: Rep[G] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))) = t; x }
    def _8: Rep[H] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, x))))))) = t; x }
  }

  implicit class TupleOps9[A,B,C,D,E,F,G,H,I](t: Rep[(A,(B,(C,(D,(E,(F,(G, (H, I))))))))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, Pair(x, _)))) = t; x }
    def _5: Rep[E] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))) = t; x }
    def _6: Rep[F] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))) = t; x }
    def _7: Rep[G] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))) = t; x }
    def _8: Rep[H] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))))) = t; x }
    def _9: Rep[I] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, x)))))))) = t; x }
  }

  implicit class TupleOps16[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P](t: Rep[(A,(B,(C,(D,(E,(F,(G,(H,(I,(J,(K,(L,(M,(N,(O,P)))))))))))))))]) {
    def _1: Rep[A] = { val Pair(x, _) = t; x }
    def _2: Rep[B] = { val Pair(_, Pair(x, _)) = t; x }
    def _3: Rep[C] = { val Pair(_, Pair(_, Pair(x, _))) = t; x }
    def _4: Rep[D] = { val Pair(_, Pair(_, Pair(_, Pair(x, _)))) = t; x }
    def _5: Rep[E] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))) = t; x }
    def _6: Rep[F] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))) = t; x }
    def _7: Rep[G] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))) = t; x }
    def _8: Rep[H] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))))) = t; x }
    def _9: Rep[I] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))))) = t; x }
    def _10: Rep[J] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))))))) = t; x }
    def _11: Rep[K] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))))))) = t; x }
    def _12: Rep[L] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))))))))) = t; x }
    def _13: Rep[M] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))))))))) = t; x }
    def _14: Rep[N] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _)))))))))))))) = t; x }
    def _15: Rep[O] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(x, _))))))))))))))) = t; x }
    def _16: Rep[P] = { val Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, Pair(_, x))))))))))))))) = t; x }
  }

  implicit def zipTuple3[A, B, C](p: (Rep[A], Rep[B], Rep[C])): Rep[(A, (B, C))] =
    Tuple(p._1, p._2, p._3)

  implicit def zipTuple4[A, B, C, D](p: (Rep[A], Rep[B], Rep[C], Rep[D])): Rep[(A, (B, (C, D)))] =
    Tuple(p._1, p._2, p._3, p._4)

  implicit def zipTuple5[A, B, C, D, E](p: (Rep[A], Rep[B], Rep[C], Rep[D], Rep[E])): Rep[(A, (B, (C, (D, E))))] =
    Tuple(p._1, p._2, p._3, p._4, p._5)

  implicit def zipTuple6[A, B, C, D, E, F](p: (Rep[A], Rep[B], Rep[C], Rep[D], Rep[E], Rep[F])): Rep[(A, (B, (C, (D, (E, F)))))] =
    Tuple(p._1, p._2, p._3, p._4, p._5, p._6)

  implicit def zipTuple7[A, B, C, D, E, F, G](p: (Rep[A], Rep[B], Rep[C], Rep[D], Rep[E], Rep[F], Rep[G])): Rep[(A, (B, (C, (D, (E, (F, G))))))] =
    Tuple(p._1, p._2, p._3, p._4, p._5, p._6, p._7)

  implicit def zipTuple8[A, B, C, D, E, F, G, H](p: (Rep[A], Rep[B], Rep[C], Rep[D], Rep[E], Rep[F], Rep[G], Rep[H])): Rep[(A, (B, (C, (D, (E, (F, (G, H)))))))] =
    Tuple(p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8)

  implicit def zipTuple9[A, B, C, D, E, F, G, H, I](p: (Rep[A], Rep[B], Rep[C], Rep[D], Rep[E], Rep[F], Rep[G], Rep[H], Rep[I])): Rep[(A, (B, (C, (D, (E, (F, (G, (H, I))))))))] =
    Tuple(p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8, p._9)

  implicit def zipTuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](p: (Rep[A], Rep[B], Rep[C], Rep[D], Rep[E], Rep[F], Rep[G], Rep[H], Rep[I], Rep[J], Rep[K], Rep[L], Rep[M], Rep[N], Rep[O], Rep[P])): Rep[(A, (B, (C, (D, (E, (F, (G, (H, (I, (J, (K, (L, (M, (N, (O, P)))))))))))))))] =
    Tuple(p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8, p._9, p._10, p._11, p._12, p._13, p._14, p._15, p._16)

  object Tuple {
    def apply[A, B](a: Rep[A], b: Rep[B]) = Pair(a, b)

    def apply[A, B, C](a: Rep[A], b: Rep[B], c: Rep[C]): Rep[(A, (B, C))] =
      Pair(a, Pair(b, c))

    def apply[A, B, C, D](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D]): Rep[(A, (B, (C, D)))] =
      Pair(a, Pair(b, Pair(c, d)))

    def apply[A, B, C, D, E](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D], e: Rep[E]): Rep[(A, (B, (C, (D, E))))] =
      Pair(a, Pair(b, Pair(c, Pair(d, e))))

    def apply[A, B, C, D, E, F](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D], e: Rep[E], f: Rep[F]): Rep[(A, (B, (C, (D, (E, F)))))] =
      Pair(a, Pair(b, Pair(c, Pair(d, Pair(e, f)))))

    def apply[A, B, C, D, E, F, G](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D], e: Rep[E], f: Rep[F], g: Rep[G]): Rep[(A, (B, (C, (D, (E, (F, G))))))] =
      Pair(a, Pair(b, Pair(c, Pair(d, Pair(e, Pair(f, g))))))

    def apply[A, B, C, D, E, F, G, H](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D], e: Rep[E], f: Rep[F], g: Rep[G], h: Rep[H]): Rep[(A, (B, (C, (D, (E, (F, (G, H)))))))] =
      Pair(a, Pair(b, Pair(c, Pair(d, Pair(e, Pair(f, Pair(g, h)))))))

    def apply[A, B, C, D, E, F, G, H, I](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D], e: Rep[E], f: Rep[F], g: Rep[G], h: Rep[H], i: Rep[I]): Rep[(A, (B, (C, (D, (E, (F, (G, (H, I))))))))] =
      Pair(a, Pair(b, Pair(c, Pair(d, Pair(e, Pair(f, Pair(g, Pair(h, i))))))))

    def apply[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](a: Rep[A], b: Rep[B], c: Rep[C], d: Rep[D], e: Rep[E], f: Rep[F], g: Rep[G], h: Rep[H], i: Rep[I], j: Rep[J], k: Rep[K], l: Rep[L], m: Rep[M], n: Rep[N], o: Rep[O], p: Rep[P]): Rep[(A, (B, (C, (D, (E, (F, (G, (H, (I, (J, (K, (L, (M, (N, (O, P)))))))))))))))] =
      Pair(a, Pair(b, Pair(c, Pair(d, Pair(e, Pair(f, Pair(g, Pair(h, Pair(i, Pair(j, Pair(k, Pair(l, Pair(m, Pair(n, Pair(o, p)))))))))))))))

    def unapply[A, B](p: Rep[(A, B)]) = Some((p._1, p._2))

    def unapply[A, B, C](p: Rep[(A, (B, C))])(implicit o: Overloaded1) =
      Some((p._1, p._2, p._3))

    def unapply[A, B, C, D](p: Rep[(A, (B, (C, D)))])(implicit o: Overloaded2) =
      Some((p._1, p._2, p._3, p._4))

    def unapply[A, B, C, D, E](p: Rep[(A, (B, (C, (D, E))))])(implicit o: Overloaded3) =
      Some((p._1, p._2, p._3, p._4, p._5))

    def unapply[A, B, C, D, E, F](p: Rep[(A, (B, (C, (D, (E, F)))))])(implicit o: Overloaded4) =
      Some((p._1, p._2, p._3, p._4, p._5, p._6))

    def unapply[A, B, C, D, E, F, G](p: Rep[(A, (B, (C, (D, (E, (F, G))))))])(implicit o: Overloaded5) =
      Some((p._1, p._2, p._3, p._4, p._5, p._6, p._7))

    def unapply[A, B, C, D, E, F, G, H](p: Rep[(A, (B, (C, (D, (E, (F, (G, H)))))))])(implicit o1: Overloaded1, o2: Overloaded1) =
      Some((p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8))

    def unapply[A, B, C, D, E, F, G, H, I](p: Rep[(A, (B, (C, (D, (E, (F, (G, (H, I))))))))])(implicit o: Overloaded6) =
      Some((p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8, p._9))

    def unapply[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](p: Rep[(A, (B, (C, (D, (E, (F, (G, (H, (I, (J, (K, (L, (M, (N, (O, P)))))))))))))))])(implicit o: Overloaded7) =
      Some((p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8, p._9, p._10, p._11, p._12, p._13, p._14, p._15, p._16))
  }

  val tuplesCache = AVHashMap[Rep[_], (Rep[_], Rep[_])](1000)

  def unzipPair[A, B](p: Rep[(A, B)]): (Rep[A], Rep[B]) = p match {
    case Def(Tup(a, b)) => (a, b)
    case _ => p.elem match {
      case pe: PairElem[_, _] =>
        implicit val eA = pe.eFst
        implicit val eB = pe.eSnd
        if (cachePairs) {
          if (!tuplesCache.containsKey(p)) {
            tuplesCache.put(p, (First(p), Second(p)))
          }
          tuplesCache(p).asInstanceOf[(Rep[A], Rep[B])]
        }
        else
          (First(p), Second(p))
      case _ =>
        !!!(s"expected Tup[A,B] or Sym with type (A,B) but got ${p.toStringWithDefinition}", p)
    }
  }

  implicit def zipPair[A, B](p: (Rep[A], Rep[B])): Rep[(A, B)] = {
    implicit val ea = p._1.elem
    implicit val eb = p._2.elem
    Tup(p._1, p._2)
  }


  case class Tup[A, B](a: Rep[A], b: Rep[B]) extends Def[(A, B)] {
    implicit val eA: Elem[A] = a.elem
    implicit val eB: Elem[B] = b.elem
    assert(null != eA && null != eB)
    lazy val selfType = element[(A,B)]
    override def transform(t: Transformer): Def[(A, B)] = Tup(t(a), t(b))
  }

  case class First[A, B](pair: Rep[(A, B)]) extends Def[A] {
    val selfType: Elem[A] = pair.elem.eFst
    override def transform(t: Transformer): Def[A] = First(t(pair))
  }

  case class Second[A, B](pair: Rep[(A, B)]) extends Def[B] {
    val selfType: Elem[B] = pair.elem.eSnd
    override def transform(t: Transformer): Def[B] = Second(t(pair))
  }

  object TupleProjection {
    def apply[A,B](t: Rep[(A,B)], i: Int): Sym = i match {
      case 1 => t._1
      case 2 => t._2
    }
    def unapply(p: Sym): Option[Int] = p match {
      case Def(First(_)) => Some(1)
      case Def(Second(_)) => Some(2)
      case _ => None
    }
  }

  def projectionIndex(p: Sym): Int = p match {
    case TupleProjection(i) => i
    case _ => !!!("tuple projection expected", p)
  }

}

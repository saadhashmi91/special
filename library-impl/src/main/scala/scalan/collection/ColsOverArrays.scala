package scalan.collection

import scala.reflect.ClassTag
import scalan.OverloadId

class ColOverArray[A](val arr: Array[A]) extends Col[A] {
  def builder = new ColOverArrayBuilder
  def length = arr.length
  def apply(i: Int) = arr(i)
  def map[B: ClassTag](f: A => B): Col[B] = builder.fromArray(arr.map(f))
  def foreach(f: A => Unit): Unit = arr.foreach(f)
  def exists(p: A => Boolean) = arr.exists(p)
  def forall(p: A => Boolean) = arr.forall(p)
  def filter(p: A => Boolean) = builder.fromArray(arr.filter(p))
  def fold[B](zero: B)(op: (B, A) => B) = arr.foldLeft(zero)(op)
  def slice(from: Int, until: Int) = builder.fromArray(arr.slice(from, until))
//  def ++(other: Col[A]) = builder.fromArray(arr ++ other.arr)
}

class PairOfCols[L,R](val ls: Col[L], val rs: Col[R]) extends PairCol[L,R] {
  override def builder: ColBuilder = new ColOverArrayBuilder
  override def arr: Array[(L, R)] = ls.arr.zip(rs.arr)
  override def length: Int = ls.length
  override def apply(i: Int): (L, R) = (ls(i), rs(i))
  override def map[V: ClassTag](f: ((L, R)) => V): Col[V] = new ColOverArray(arr.map(f))
  override def foreach(f: ((L, R)) => Unit): Unit = arr.foreach(f)
  override def exists(p: ((L, R)) => Boolean) = arr.exists(p)
  override def forall(p: ((L, R)) => Boolean) = arr.forall(p)
  override def filter(p: ((L, R)) => Boolean): Col[(L,R)] = new ColOverArray(arr.filter(p))
  override def fold[B](zero: B)(op: (B, (L, R)) => B) = arr.foldLeft(zero)(op)
  override def slice(from: Int, until: Int) = builder(ls.slice(from, until), rs.slice(from, until))
//  override def ++(other: Col[(L, R)]) =
}

class ColOverArrayBuilder extends ColBuilder {
  @OverloadId("apply")       def apply[A, B](as: Col[A], bs: Col[B]): PairCol[A, B] = new PairOfCols(as, bs)
  @OverloadId("apply_items") def apply[T](items: T*): Col[T] = ???
  def fromArray[T](arr: Array[T]): Col[T] = new ColOverArray[T](arr)
  def replicate[T:ClassTag](n: Int, v: T) = fromArray(Array.fill(n)(v))
  def dot[A](xs: Col[A], ys: Col[A]): A = ???
}

class ArrayFunctor extends Functor[Array] {
  override def map[A, B](fa: Array[A])(f: (A) => B)(implicit tB: ClassTag[B]): Array[B] = fa.map(f)
}

//  object ColOverArray {
//    def fromArray[T](arr: Array[T]): Col[T] = new ColOverArray(arr)
//  }
//  class PairCol[A, B](val as: Col[A], val bs: Col[B]) extends Col[(A, B)] {
//    def arr: Array[(A, B)] = (as.arr zip bs.arr)
//    def length = as.length
//    def apply(i: Int) = (as(i), bs(i))
//  }


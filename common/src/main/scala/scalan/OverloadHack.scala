package scalan

// hack to appease erasure

object OverloadHack {
  trait Overloaded
  class Overloaded1 extends Overloaded { override def toString = "O1"}
  class Overloaded2 extends Overloaded { override def toString = "O2"}
  class Overloaded3 extends Overloaded { override def toString = "O3"}
  class Overloaded4 extends Overloaded { override def toString = "O4"}
  class Overloaded5 extends Overloaded { override def toString = "O5"}
  class Overloaded6 extends Overloaded { override def toString = "O6"}
  class Overloaded7 extends Overloaded { override def toString = "O7"}
  implicit val overloaded1 = new Overloaded1
  implicit val overloaded2 = new Overloaded2
  implicit val overloaded3 = new Overloaded3
  implicit val overloaded4 = new Overloaded4
  implicit val overloaded5 = new Overloaded5
  implicit val overloaded6 = new Overloaded6
  implicit val overloaded7 = new Overloaded7
}
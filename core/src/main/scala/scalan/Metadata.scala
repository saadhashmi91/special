package scalan

trait Metadata { self: Scalan =>
  // exists only to avoid duplicate keys
  private[this] var metaKeyNames = Set.empty[String]

  // all metadata is currently global, may add scoping in the future
  // Elem[A] ensures it's a known serializable type so that external tools can
  // handle metadata. We may weaken this restriction later
  /**
    * Key for metadata of type `A`.
    */
  case class MetaKey[A](name: String)(implicit val eA: Elem[A]) {
    if (metaKeyNames.contains(name)) {
      !!!(s"Duplicate metadata key: $name")
    } else {
      metaKeyNames += name
    }
  }

  /**
    * Value for metadata of type `A`.
    * @param mirrorWithDef
    *   None - mirror always,
    *  Some(true) - mirror during graph mirroring and rewriting of current stage (used for Analyzer markings)
    *  Some(false) - don't mirror during stage mirroring
    */
  case class MetaValue[A](value: A, mirrorWithDef: Option[Boolean] = None)

  /**
    * Sets metadata for the target. No-op in sequential context,
    * but the metadata can be accessed, transformed, etc. in staged context.
    *
    * Returns the target for chaining.
    */

  implicit class MetadataOps[A](target: Rep[A]) {
    def setMetadata[B](key: MetaKey[B])(value: B, mirrorWithDef: Option[Boolean] = None) = {
      self.setMetadata(target, key)(value, mirrorWithDef)
    }
    def withName(name: String) = setMetadata(symNameKey)(name, Some(true))
  }

  // TODO better do it with UnboxedTuple, similar to LMS
  val MultipleArgsKey = MetaKey[Int]("emitMuplipleArgs")
  val symNameKey = MetaKey[String]("symName")

  implicit class MultipleArgs(f: Rep[_ => _]) {
    def multipleArgs(n: Int) = f.setMetadata(MultipleArgsKey)(n)
  }

  case class MetaNode(val meta: Map[MetaKey[_], MetaValue[Any]]) {
    def get[A](key: MetaKey[A]) = meta.get(key).map(_.value).asInstanceOf[Option[A]]

    def set[A](key: MetaKey[A])(value: A, mirrorWithDef: Option[Boolean] = None) =
      new MetaNode(meta.updated(key, MetaValue(value, mirrorWithDef)))

    def remove[A](key: MetaKey[A]) = new MetaNode(meta - key)

    def extendWith(other: MetaNode) = new MetaNode(meta ++ other.meta.toSeq)

    def updateIfExists[A](key: MetaKey[A])(f: A => A) = get(key) match {
      case None => this
      case Some(value) => set(key)(f(value))
    }

    def update[A](key: MetaKey[A], default: A)(f: A => A) = get(key) match {
      case None => set(key)(default)
      case Some(value) => set(key)(f(value))
    }
    def filterSinglePass: MetaNode = {
      MetaNode(meta.collect {
        case (k, MetaValue(v, Some(true))) => k -> MetaValue(v, Some(false))
        case kv @ (_, MetaValue(_, None)) => kv
      })
    }
  }

  object MetaNode {
    val empty = new MetaNode(Map.empty)
  }

  private[scalan] var metadataPool = Map.empty[Sym, MetaNode]

  def allMetadataOf(target: Rep[_]): MetaNode = metadataPool.getOrElse(target, MetaNode.empty)

  protected[scalan] def setAllMetadata(target: Rep[_], node: MetaNode) = {
    val newNode = metadataPool.get(target) match {
      case None => node
      case Some(oldNode) => oldNode.extendWith(node)
    }
    metadataPool += target -> newNode
  }

  def setMetadata[A, B](target: Rep[A], key: MetaKey[B])(value: B, mirrorWithDef: Option[Boolean] = None): Rep[A] = {
    val node = allMetadataOf(target)
    metadataPool += target -> node.set(key)(value, mirrorWithDef)
    target
  }

  def getMetadata[A](target: Sym, key: MetaKey[A]): Option[A] =
    allMetadataOf(target).get(key)

  def removeMetadata[A](target: Sym, key: MetaKey[A]): Unit = {
    metadataPool.get(target) match {
      case Some(node) =>
        if (node.meta.contains(key)) {
          val newNode = node.remove(key)
          metadataPool += target -> newNode
        }
      case _ =>
    }
  }

  implicit class MetadataOpsExp(target: Rep[_]) {
    def getMetadata[A](key: MetaKey[A]): Option[A] = self.getMetadata(target, key)
    def removeMetadata[A](key: MetaKey[A]): Unit = self.removeMetadata(target, key)

    def allMetadata = self.allMetadataOf(target)
  }
}
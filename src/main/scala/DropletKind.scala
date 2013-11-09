sealed trait DropletKind {
  val kind: String
  val count: Int
  def isSaltMaster(n: Int) = false
  override def toString = kind
}
object DropletKind {
  val kinds = List(Front, Db)
  val totalCount = kinds.map(_.count).sum
}
case object Front extends DropletKind {
  val kind = "front"
  val count = 2
  /**
   * First front server will be master.
   */
  override def isSaltMaster(n: Int) = n == 1
}
case object Db extends DropletKind {
  val kind = "db"
  val count = 3
}

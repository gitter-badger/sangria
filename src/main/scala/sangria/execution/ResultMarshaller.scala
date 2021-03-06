package sangria.execution

trait ResultMarshaller {
  type Node

  def emptyMapNode: Node
  def addMapNodeElem(node: Node, key: String, value: Node): Node
  def mapNode(keyValues: Seq[(String, Node)]): Node
  def arrayNode(values: Seq[Node]): Node
  def emptyArrayNode: Node
  def addArrayNodeElem(array: Node, elem: Node): Node
  def isEmptyArrayNode(array: Node): Boolean

  def stringNode(value: String): Node
  def intNode(value: Int): Node
  def bigIntNode(value: BigInt): Node
  def floatNode(value: Double): Node
  def bigDecimalNode(value: BigDecimal): Node
  def booleanNode(value: Boolean): Node
  def nullNode: Node

  def renderCompact(node: Node): String
  def renderPretty(node: Node): String
}

object ResultMarshaller {
  implicit val defaultResultMarshaller = new ScalaResultMarshaller
}

class ScalaResultMarshaller extends ResultMarshaller {
  type Node = Any

  override def booleanNode(value: Boolean) = value
  override def floatNode(value: Double) = value
  override def stringNode(value: String) = value
  override def intNode(value: Int) = value
  override def bigIntNode(value: BigInt) = value
  override def bigDecimalNode(value: BigDecimal) = value

  override def arrayNode(values: Seq[Node]) = values
  override def emptyArrayNode = Nil
  override def addArrayNodeElem(array: Node, elem: Node) = array.asInstanceOf[List[_]] :+ elem
  override def isEmptyArrayNode(array: Node) = array.asInstanceOf[List[_]].isEmpty

  override def mapNode(keyValues: Seq[(String, Node)]) = Map(keyValues: _*)
  override def emptyMapNode = Map.empty[String, Any]
  override def addMapNodeElem(node: Node, key: String, value: Node) =
    node.asInstanceOf[Map[String, Any]] + (key -> value)

  override def nullNode = null

  def renderCompact(node: Any) = "" + node
  def renderPretty(node: Any) = "" + node
}

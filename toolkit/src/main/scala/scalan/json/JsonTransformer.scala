package scalan.json

import scalan.Scalan
import spray.json._

abstract class TransformPipeline[C <: Scalan](val ctx: C) {
  def apply(graph: ctx.PGraph): ctx.PGraph
}

class JsonTransformer[C <: ToolkitScalan](val pipeline: TransformPipeline[C]) {
  val jsonProtocol = new ScalanJsonProtocol[pipeline.ctx.type](pipeline.ctx)
  import jsonProtocol.ProgramGraphFormat
  import pipeline.ctx._

  def apply(jsSource: JsValue): JsValue = {
    val sourceGraph = jsSource.convertTo[PGraph]
    val gResult = pipeline(sourceGraph)
    val jsResult = gResult.toJson
    jsResult
  }

  def apply(source: String): String = {
    val jsSource = source.parseJson
    val jsResult = apply(jsSource)
    jsResult.prettyPrint
  }

  def read(json: String): PGraph = {
    val jsSource = json.parseJson
    jsSource.convertTo[PGraph]
  }
}

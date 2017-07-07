package sd.model

import play.api.libs.json.Json

case class PayModelReduce(n: Long, c: Long)
object PayModelReduce {
  implicit val fmt = Json.format[PayModelReduce]
}

class PayModel(val uid: Int, val n: Long, val c: Long)

object PayModel {
  def apply(uid: Int, value: String) = {
    val obj = Json.parse(value).as[PayModelReduce]
    new PayModel(uid, obj.n, obj.c)
  }
}


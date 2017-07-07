package sd.model

import play.api.libs.json.Json

case class GoogleWallet(uid: Int, productId: String,
  signature: String, orderId: String,
  packageName: String, purchaseTime: Long,
  purchaseState: Int, purchaseToken: String)
object GoogleWallet { implicit val reader = Json.format[GoogleWallet] }

object B3CardApi {
  private val fakeCards = Map(
    "15432637648" -> 10000,
    "15432637649" -> 50000,
    "15432637650" -> 100000,
    "15432637651" -> 1000000
  )

  private def uidToLog(uid: Option[Int], addr: Addr): String = uid.fold("")(Integer.toString(_, 36) + "|") + addr.rnd

  def logToUid(logUid: String): Int = logUid.indexOf('|') match {
    case -1 => 0
    case i => Integer.parseInt(logUid.substring(0, i), 36)
  }
}

class Addr private (val rnd: String, val sign: String, val value: String)

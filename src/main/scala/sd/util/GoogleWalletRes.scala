package sd.util

import java.sql.Connection
import java.util.Collections
import javax.inject.{ Inject, Singleton }

import anorm.SqlParser._
import anorm._
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.androidpublisher.{ AndroidPublisher, AndroidPublisherScopes }
import org.joda.time.DateTime
import play.api.db.Database
import play.api.{ Environment, Logger }
import sd.model.GoogleWallet
import sd.pay.Promote

import scala.util.Try

@Singleton
class GoogleWalletRes @Inject() (env: Environment, db: Database) extends Promote {
  //private val clientId = "675999957608-alktincsue3eg7htgsg84bmpdemlj94q.apps.googleusercontent.com"
  //private val logger = Logger(this.getClass)

  val promote = 0 // phí trả cho google khoảng 25%.
  private val email = "675999957608-alktincsue3eg7htgsg84bmpdemlj94q@developer.gserviceaccount.com"
  @inline private def keyFile = env.getFile("/src/main/resources/google-wallet-key.p12")
  private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
  private val jsonFactory = JacksonFactory.getDefaultInstance

  private lazy val credential = new GoogleCredential.Builder()
    .setTransport(httpTransport)
    .setJsonFactory(jsonFactory)
    .setServiceAccountId(email)
    .setServiceAccountScopes(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
    .setServiceAccountPrivateKeyFromP12File(keyFile)
    .build()

  private lazy val pub = new AndroidPublisher.Builder(httpTransport, jsonFactory, credential)
    .build()

  private val SqlPlayStoreCheck = SQL(
    """SELECT count(*) FROM play_store_purchase
     | WHERE purchaseToken = {purchaseToken}
     | AND productId = {productId}
     | AND packageName = {packageName}""".stripMargin
  )

  private val SqlPlayStoreInsert = SQL(
    """INSERT INTO play_store_purchase(purchaseToken, orderId, uid, purchaseTime, packageName, productId, amount)
      |VALUES({purchaseToken}, {orderId}, {uid}, {purchaseTime}, {packageName}, {productId}, {amount})""".stripMargin
  )

  private val SqlPlayStoreUpdate = SQL(
    """UPDATE play_store_purchase SET amount = {amount} WHERE orderId = {orderId}""".stripMargin
  )

  /* lưu lại thông tin để cộng tiền cho user khi validate với google server bị lỗi mạng. amount = 0 */
  private def insertPlayStoreLog(p: GoogleWallet)(implicit conn: Connection) =
    SqlPlayStoreInsert.on(
      "purchaseToken" -> p.purchaseToken,
      "productId" -> p.productId,
      "orderId" -> p.orderId,
      "uid" -> p.uid,
      "purchaseTime" -> p.purchaseTime / 1000,
      "packageName" -> p.packageName,
      "amount" -> 0
    ).executeUpdate()

  /* đã cộng bảo cho user => amount > 0*/
  private def updatePlayStoreLog(p: GoogleWallet, amount: Long)(implicit conn: Connection) =
    SqlPlayStoreUpdate.on(
      "orderId" -> p.orderId,
      "amount" -> amount
    ).executeUpdate()

  private def checkHasAddCoin(p: GoogleWallet)(implicit conn: Connection) = {
    val count = SqlPlayStoreCheck.on(
      "purchaseToken" -> p.purchaseToken,
      "productId" -> p.productId,
      "packageName" -> p.packageName
    ).as(scalar[Long].singleOpt).getOrElse(0L)
    count
  }

  def validatePayment(p: GoogleWallet): Unit = Try {
    println(" validatePayment ")
    if (p.purchaseState != 0) { // check thông tin client gửi lên chưa purchase.
      println(" Chua purchase ")
    } else {
      //      db.withTransaction { implicit conn =>
      //        if (checkHasAddCoin(p) > 0 ){
      //          println("Đã cộng bảo giao dịch Giao dịch thất bại")
      //          return
      //        }
      //       // insertPlayStoreLog(p)
      //      }
      val product = pub.purchases().products().get(p.packageName, p.productId, p.purchaseToken).execute()
      // đã purchase và check uid trùng với payLoad.
      println(s"check getPurchaseState= ${product.getPurchaseState} ")
      if (product.getPurchaseState == 0 && product.getDeveloperPayload == p.uid.toString) {
        val promoteDate = new DateTime(p.purchaseTime).getDayOfYear
        val vnd = p.productId.toInt * 1000
        val msg = s"Nạp Bảo qua Google Wallet mệnh giá $vnd"
        println(msg)
        //        coinOps.
        //          addVnd(p.uid, vnd, msg, promote, promoteDate).
        //          map {
        //            case (_, amount, newAcc) =>
        //              db.withTransaction(updatePlayStoreLog(p, amount)(_))
        //              logger.info(s"$amount ${newAcc.b}")
        //          }
      } else {
        println("Giao dịch thất bại")
      }
    }
  }.recover {
    case e: Throwable =>
      Logger.error("GoogleWalletRes.validatePayment failed", e)
  }.get
}

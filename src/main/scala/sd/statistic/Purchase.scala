package sd.statistic

import java.text.SimpleDateFormat
import java.util.{ Date, TimeZone }
import javax.inject.{ Inject, Singleton }

import akka.event.Logging
import anorm._
import play.api.db.{ Database, _ }
import sd.cb.CB
import anorm.SqlParser._
import org.joda.time.DateTime
import play.api.Logger
import sd.model.B3CardApi

import scala.util.Try

@Singleton
class Purchase @Inject() (cb: CB, @NamedDatabase("ht") htDb: Database, db: Database) {

  private val logger = Logger(this.getClass)

  case class SelectFromCard(uid: Int, seri: String, pin: String, time: Long, card: String, amount: Int)
  case class SelectFromCard1(uid: Int, time: Long, card: String, amount: Int)

  case class XFUser(uid: Int, username: String, email: String, sdt: String)

  case class PLStore(uid: Int, amount: Long)

  private val parser = (str("uid") ~ str("seri") ~ str("pin") ~ int("updated") ~ str("type") ~ int("amount") map {
    case uid ~ seri ~ pin ~ updated ~ tpe ~ amount => SelectFromCard(B3CardApi.logToUid(uid), seri, pin, updated, tpe, amount)
  }).*

  private val parser1 = (int("uid") ~ int("updated") ~ str("type") ~ int("amount") map {
    case uid ~ updated ~ tpe ~ amount => SelectFromCard1(B3CardApi.logToUid(uid.toString), updated, tpe, amount)
  }).*

  private val parser2 = (int("uid") ~ str("productId") map {
    case uid ~ productId => PLStore(uid, productId.toInt * 1000)
  }).*

  private val parser3 = (int("uid") ~ str("product_id") map {
    case uid ~ productId => {
      val value = if (productId.charAt(0) == 'c' || productId.charAt(0) == 'p') {
        productId.replaceAll("c", "").replaceAll("p", "").toInt * 20000
      } else {
        productId.toInt * 1000
      }
      PLStore(uid, value)
    }
  }).*

  private val parser4 = (int("uid") ~ int("vnd") map {
    case uid ~ vnd => PLStore(uid, vnd)
  }).*

  private def sql(partner: String) = SQL(s"SELECT uid, seri, pin, updated, type, amount FROM $partner p WHERE p.updated > {from} AND p.updated < {to} and p.status >= 0 and p.amount > 0")

  private val ParserGetUser = (int("uid") ~ str("username") ~ str("email") ~ get[Option[String]]("sdt")).map {
    case a ~ b ~ c ~ d => XFUser(a, b, c, d.getOrElse(""))
  }.single

  private val xfuser = SQL(s"SELECT u.user_id uid, u.username username, u.email email, b.field_value sdt FROM xf_user u LEFT JOIN xf_user_field_value b ON u.user_id = b.user_id AND b.field_id = 'sdt' WHERE u.user_id = {uid}")

  def getAll(partner: String, from: Long, to: Long) = sql(partner).on("from" -> from, "to" -> to)

  private val LastestUser = SQL("SELECT user_id FROM xf_user p WHERE  p.register_date > {from} AND p.register_date < {to} ORDER BY user_id LIMIT 1")

  private val NewestUser = SQL("SELECT user_id FROM xf_user p WHERE p.register_date > {from} AND p.register_date < {to} ORDER BY user_id DESC LIMIT 1")

  private val PlayStoreSql = SQL("SELECT uid, productId FROM play_store_purchase p WHERE p.purchaseTime > {from} AND p.purchaseTime < {to}")

  private val AppleStoreSql = SQL("SELECT uid, product_id FROM apple_store_purchase p WHERE p.purchase_date_ms > {from} AND p.purchase_date_ms < {to}")

  private val ModLogSql = SQL("SELECT uid, vnd FROM mod_log p WHERE p.vnd > 0 AND p.vnd <= 10000000 AND UNIX_TIMESTAMP(created) > {from} AND UNIX_TIMESTAMP(created) < {to}")

  val partners = Array("gate_log_card", "hopepay_log_card", "paydirect_log_card", "vdc_log_card", "vtc_log_card", "nganluong_log_card")

  def getMinMaxUid(from: Long, to: Long): (Int, Int) = db.withConnection { implicit conn =>
    val minUid = LastestUser.on("from" -> from, "to" -> to).as(scalar[Int].singleOpt).getOrElse(0)
    val maxUid = NewestUser.on("from" -> from, "to" -> to).as(scalar[Int].singleOpt).getOrElse(0)
    (minUid, maxUid)
  }

  def newUserHasPurchase(from: Long, to: Long): Unit = {
    val (minUid, maxUid) = getMinMaxUid(from, to)
    logger.error(s"minUid = $minUid maxUid = $maxUid số lượng tạo mới + ${maxUid - minUid}")

    val retTotal = allPurChase(from, to)

    retTotal.filter { u => u._1 >= minUid && u._1 < maxUid }.map {
      case (uid: Int, sum: Long) =>
        val u = db.withConnection { implicit conn =>
          xfuser.on("uid" -> uid).as(ParserGetUser)
        }
        (u, sum)
    }.toList.sortBy(-_._2).foreach {
      case (u: XFUser, sum: Long) =>
        logger.error("newUser " + u.uid + " " + u.username + " email: " + u.email + " sdt: " + u.sdt + " total:" + sum + "\n")
    }
  }

  def allPurChase(from: Long, to: Long) = {
    //    val l1 = htDb.withConnection { implicit conn =>
    //      partners.foldRight(Seq.empty[SelectFromCard])((partner: String, sum: Seq[SelectFromCard]) =>
    //        sum ++ getAll(partner, from, to).as(parser))
    //    }

    val l2 = db.withConnection { implicit conn =>
      getAll("1pay_log", from, to).as(parser1)
    }

    val l4 = db.withConnection { implicit conn =>
      PlayStoreSql.on("from" -> from, "to" -> to).as(parser2)
    }

    val l5 = db.withConnection { implicit conn =>
      AppleStoreSql.on("from" -> from * 1000, "to" -> to * 1000).as(parser3)
    }

    val l6 = db.withConnection { implicit conn =>
      ModLogSql.on("from" -> from, "to" -> to).as(parser4)
    }
    //val l3 = l1 ++ l2
    val l3 = l2
    val ret1 = l3.groupBy(_.uid).map { p =>
      (p._1, p._2.foldRight(0L)((c, sum) => sum + c.amount))
    }.toList
    val ret2 = (l4 ++ l5 ++ l6).groupBy(_.uid).map { p =>
      (p._1, p._2.foldRight(0L)((c, sum) => sum + c.amount))
    }.toList

    val retTotal = (ret1 ++ ret2).groupBy(_._1).map { p =>
      (p._1, p._2.foldRight(0L)((c, sum) => sum + c._2))
    }
    retTotal
  }

  def allPurChaseLog(from: Long, to: Long) = {
    val l1 = htDb.withConnection { implicit conn =>
      partners.foldRight(Seq.empty[SelectFromCard])((partner: String, sum: Seq[SelectFromCard]) =>
        sum ++ getAll(partner, from, to).as(parser))
    }

    //    val l2 = db.withConnection { implicit conn =>
    //      getAll("1pay_log", from, to).as(parser1)
    //    }
    //
    //    val l4 = db.withConnection { implicit conn =>
    //      PlayStoreSql.on("from" -> from, "to" -> to).as(parser2)
    //    }
    //
    //    val l5 = db.withConnection { implicit conn =>
    //      AppleStoreSql.on("from" -> from * 1000, "to" -> to * 1000).as(parser3)
    //    }
    //
    //    val l6 = db.withConnection { implicit conn =>
    //      ModLogSql.on("from" -> from, "to" -> to).as(parser4)
    //    }

    val l3 = l1.sortBy(_.time)
    l3.foreach { u =>
      val date = new Date(u.time * 1000)
      val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z"); // the format of your date
      sdf.setTimeZone(TimeZone.getTimeZone("GMT+7")); // give a timezone reference for formating (see comment at the bottom
      val formattedDate = sdf.format(date)
      logger.error(s"uid = ${u.uid} | seri = ${u.seri}, pin = ${u.pin}, time = ${u.time} | amount = ${u.amount} | card = ${u.card}")
      println(s"uid = ${u.uid} | seri = ${u.seri}, pin = ${u.pin}, time = $formattedDate | amount = ${u.amount} | card = ${u.card}")
    }
    //    val ret1 = l3.groupBy(_.uid).map { p =>
    //      (p._1, p._2.foldRight(0L)((c, sum) => sum + c.amount))
    //    }.toList
    //    val ret2 = (l4 ++ l5 ++ l6).groupBy(_.uid).map { p =>
    //      (p._1, p._2.foldRight(0L)((c, sum) => sum + c.amount))
    //    }.toList
    //
    //    val retTotal = (ret1 ++ ret2).groupBy(_._1).map { p =>
    //      (p._1, p._2.foldRight(0L)((c, sum) => sum + c._2))
    //    }
    //    retTotal
  }

  def vip(from: Long, to: Long, total: Long = 12000000) = {

    val retTotal = allPurChase(from, to)

    retTotal.filter { u => u._2 >= total && u._1 > 0 }.map {
      case (uid: Int, sum: Long) =>
        val u = db.withConnection { implicit conn =>
          xfuser.on("uid" -> uid).as(ParserGetUser)
        }
        (u, sum)
    }.toList.sortBy(-_._2).foreach {
      case (u: XFUser, sum: Long) =>
        logger.error(s"${u.uid}|${u.username}|${u.email}|${u.sdt}|$sum\r\n")
    }
  }

  def vipNum(from: Long, to: Long, total: Int = 100) = {

    val retTotal = allPurChase(from, to)

    retTotal.filter { u => u._1 > 0 }.toList.sortBy(-_._2).take(total).map {
      case (uid: Int, sum: Long) =>
        Try {
          val u = db.withConnection { implicit conn =>
            xfuser.on("uid" -> uid).as(ParserGetUser)
          }
          (u, sum)
        }.getOrElse((XFUser(uid, "", "", ""), sum))
    }.foreach {
      case (u: XFUser, sum: Long) =>
        logger.error(u.uid + "|" + u.username + "|" + u.email + "|" + u.sdt + "|" + sum + "\r\n")
    }

    //    retTotal.filter{ u => u._2 >= total && u._1 > 0}.map { case (uid: Int, sum: Long) =>
    //      val u = db.withConnection { implicit conn =>
    //        xfuser.on("uid" -> uid).as(ParserGetUser)
    //      }
    //      (u, sum)
    //    }.toList.sortBy(- _._2).foreach{ case (u:XFUser, sum: Long) =>
    //      logger.error("vip " + u.uid + " " + u.username + " email: " + u.email + " sdt: " + u.sdt + " total:" + sum + "\n")
    //    }
  }

  def logabc(from: Long, to: Long) = {
    val l4 = db.withConnection { implicit conn =>
      PlayStoreSql.on("from" -> from, "to" -> to).as(parser2)
    }

    val l5 = db.withConnection { implicit conn =>
      AppleStoreSql.on("from" -> from * 1000, "to" -> to * 1000).as(parser3)
    }

    //    val l6 = db.withConnection { implicit conn =>
    //      ModLogSql.on("from" -> from, "to" -> to).as(parser4)
    //    }
    //
    val ret2 = (l4 ++ l5).groupBy(_.uid).map { p =>
      (p._1, p._2.foldRight(0L)((c, sum) => sum + c.amount))
    }.toList

    val retTotal = ret2.groupBy(_._1).map { p =>
      (p._1, p._2.foldRight(0L)((c, sum) => sum + c._2))
    }

    retTotal.filter { u => u._2 >= 100000 && u._1 > 0 }.map {
      case (uid: Int, sum: Long) =>
        val u = db.withConnection { implicit conn =>
          xfuser.on("uid" -> uid).as(ParserGetUser)
        }
        (u, sum)
    }.toList.sortBy(-_._2).foreach {
      case (u: XFUser, sum: Long) =>
        println(s"${u.uid}|${u.username}|${u.email}|${u.sdt}|$sum\r\n")
    }
  }
  def logCardErr() = {}

}

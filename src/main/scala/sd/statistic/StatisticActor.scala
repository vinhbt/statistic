package sd.statistic

import javax.inject.Inject

import akka.actor.Actor
import akka.event.Logging
import anorm.SqlParser._
import anorm._
import com.couchbase.client.java.document.json.JsonArray
import com.couchbase.client.java.view.{ AsyncViewRow, ViewQuery }
import com.sandinh.rx.Implicits._
import play.api.db.{ Database, NamedDatabase }
import sd.admin.Connect
import sd.cb._
import sd.model.{ GoogleWallet, PayModel }
import sd.util.GoogleWalletRes

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

case class FreeCoin(from: Long, to: Long)

case class TaxEarn(from: Long, to: Long)

case class TaxRange(from: Long, to: Long)

case class SumPayVnd(from: Long, to: Long)

private case class PayRange(month: Int, uid: Int)

case class PayRangeStart(month: Int)

private object PayRangeEnd

case class SumPayVndByDb(from: Long, to: Long)

case class NewUserByDb(from: Long, to: Long)

class StatisticActor @Inject() (cb: CB, @NamedDatabase("ht") htDb: Database, db: Database, googleWalletRes: GoogleWalletRes) extends Actor {

  private val isDevelope = false

  private val logger = Logging(context.system, this)

  private val PayRangeGetLimit = 50

  private val payRangeList = mutable.Buffer.empty[PayModel]

  private val SumValue = scalar[Long].singleOpt

  private def SumAmount(partner: String) = SQL(s"SELECT sum(p.amount) as amount FROM $partner p WHERE p.updated > {from} AND p.updated < {to}")

  private val CountNewUser = SQL("SELECT count(*) FROM xf_user p WHERE p.register_date > {from} AND p.register_date < {to}")

  override def receive: Receive = {

    case FreeCoin(from: Long, to: Long) => getTotalFree(from, to).onComplete {
      case Failure(e) => logger.error("FreeCoin Err {}", e)
      case Success(t) =>
        logger.error(" FreeCoin số user get free_coin 1 lần, 2 lần, 3 lần, 4 lần, 5 lần.")
        logger.error("FreeCoin {} ", t)
    }

    case TaxEarn(from: Long, to: Long) => getTotalTax(from, to).onComplete {
      case Failure(e) => logger.error("TaxEarn Err {}", e)
      case Success(t) =>
        logger.error("Phế sân đình kiếm được: ")
        logger.error("TaxEarn = {}", t)
    }

    case TaxRange(from: Long, to: Long) => getTotalTaxRange(from, to).onComplete {
      case Failure(e) => logger.error("TaxRange Err {}", e)
      case Success(t) =>
        logger.error("Phế sân đình thu được ứng với các mốc <= 500K, 500K < x <= 1M, 1M < x <= 2M, 2M < x <= 5M, > 5M")
        logger.error("TaxRange = {}", t)
    }

    case SumPayVnd(from: Long, to: Long) => sumPayVnd(from, to).onComplete {
      case Failure(e) => logger.error("SumPayVnd Err {}", e)
      case Success(t) => {
        logger.info("Số tiền VND thu được  Nạp thẻ, SMS, tiền mặt, google wallet, apple wallet, số bảo user nhận được:")
        logger.error(t.asScala.mkString(", "))
      }
    }

    case PayRangeStart(month: Int) =>
      payRangeList.clear()
      self ! PayRange(month, 0)

    case PayRange(month: Int, uid: Int) => paidRange(month, uid)

    case PayRangeEnd => paidRangeEnd()

    case SumPayVndByDb(from: Long, to: Long) => logger.error("SumPayVndByDb {}", total_log_card(from, to).get + total_sms(from, to).get)

    case NewUserByDb(from: Long, to: Long) => countNewUser(from, to)

    case Connect =>
      val s = GoogleWallet(758320, "50", "abc", "GPA.1396-1891-4069-04978", "com.chanpro.Chan", 1476796538L, 0, "mnneihkiakmpldmhfgenocbo.AO-J1Ow_gXSdU0NTHrVhSboFDD-XKOnHm8QYhP6bsquOzLjFT8muGBvC_6rhUnQTmfjg6H8IhldBhMQpxrkpUp_FGz2WVyYKvPfrk8jSFMuiwHBsGjfs8-A")

      googleWalletRes.validatePayment(s)

  }

  private def paidRangeEnd(): Unit = {

    logger.error("paidRangeEnd Info")
    //payRangeList.foreach(p => logger.debug("paidRangeEnd {}", p.uid))

    val lessThan20k = payRangeList.filter(p => p.n < 20000)
    val from20To100K = payRangeList.filter(p => p.n >= 20000 && p.n < 100000)
    val from100To500K = payRangeList.filter(p => p.n >= 100000 && p.n < 500000)
    val from500To1M = payRangeList.filter(p => p.n >= 500000 && p.n < 1000000)
    val from1MTo2M = payRangeList.filter(p => p.n >= 1000000 && p.n < 2000000)
    val from2MTo = payRangeList.filter(p => p.n >= 2000000)

    logger.error(
      "paidRangeEnd Total count = {}, total vnd = {}, total Bao = {}",
      payRangeList.length, payRangeList.foldLeft(0L)(_ + _.n), payRangeList.foldLeft(0L)(_ + _.c)
    )

    logger.error(
      "paidRangeEnd < 20K count = {}, total vnd = {}, total Bao = {}",
      lessThan20k.length, lessThan20k.foldLeft(0L)(_ + _.n), lessThan20k.foldLeft(0L)(_ + _.c)
    )

    logger.error(
      "paidRangeEnd > 20K and < 100K count = {}, total vnd = {}, total Bao = {}",
      from20To100K.length, from20To100K.foldLeft(0L)(_ + _.n), from20To100K.foldLeft(0L)(_ + _.c)
    )

    logger.error(
      "paidRangeEnd > 100K and < 500K count = {}, total vnd = {}, total Bao = {}",
      from100To500K.length, from100To500K.foldLeft(0L)(_ + _.n), from100To500K.foldLeft(0L)(_ + _.c)
    )

    logger.error(
      "paidRangeEnd > 500K and < 1M count = {}, total vnd = {}, total Bao = {}",
      from500To1M.length, from500To1M.foldLeft(0L)(_ + _.n), from500To1M.foldLeft(0L)(_ + _.c)
    )

    logger.error(
      "paidRangeEnd > 1M and < 2M count = {}, total vnd = {}, total Bao = {}",
      from1MTo2M.length, from1MTo2M.foldLeft(0L)(_ + _.n), from1MTo2M.foldLeft(0L)(_ + _.c)
    )

    logger.error(
      "paidRangeEnd > 2M count = {}, total vnd = {}, total Bao = {}",
      from2MTo.length, from2MTo.foldLeft(0L)(_ + _.n), from2MTo.foldLeft(0L)(_ + _.c)
    )

  }

  private def row2PayModel(row: AsyncViewRow): PayModel = {

    val ret = mutable.Buffer.empty[Int]
    //[tourType, app, winTime]
    row.key.asInstanceOf[JsonArray].asScala.foreach(ret += _.toString.toInt)

    PayModel(ret.last, row.value().toString)
  }

  private def paidRange(month: Int, uid: Int): Unit = {

    val startUid = uid + 1

    println(s"paidRange $month, $startUid")

    val query = ViewQuery
      .from("statistic", "pay_range")
      .development(isDevelope)
      .group()
      .reduce()
      .startKey(JsonArray.empty + month + startUid)
      .endKey(JsonArray.empty + (month + 1) + 0)
      .limit(PayRangeGetLimit)

    //println(" query = {}" +  query.toString)
    cb.log
      .query(query)
      .flatMap(asynView => asynView.rows.toList.toFuture)
      .map(t => t.asScala.map(row2PayModel))
      .onComplete {
        case Success(rows) =>
          rows.foreach(row => payRangeList += row)
          if (rows.length > 0) {
            self ! PayRange(month, rows.last.uid)
          } else {
            self ! PayRangeEnd
          }

        case Failure(x) =>
          logger.error("paidRange Err {}", x)
          self ! PayRangeEnd
      }

  }

  private def sumPayVnd(from: Long, to: Long): Future[JsonArray] = {
    println(s"sumPayVnd $from, $to")
    val query = ViewQuery
      .from("statistic", "pay")
      .development(isDevelope)
      .reduce()
      .startKey(from)
      .endKey(to)

    cb.log
      .query(query)
      .flatMap(asynView => asynView.rows.toFuture)
      .map(t => t.value().asInstanceOf[JsonArray])
  }

  private def getTotalFree(from: Long, to: Long): Future[String] = {
    println(s"getTotalFree $from, $to")

    val query = ViewQuery
      .from("statistic", "free_coin")
      .development(isDevelope)
      .reduce()
      .startKey(from)
      .endKey(to)

    cb.log
      .query(query)
      .flatMap(asynView => asynView.rows.toFuture)
      .map(t => t.value().toString)
  }

  private def getTotalTax(from: Long, to: Long): Future[String] = {

    println(s"getTotalTax $from, $to")
    val query = ViewQuery
      .from("statistic", "tax")
      .development(isDevelope)
      .reduce()
      .startKey(from)
      .endKey(to)

    cb.log
      .query(query)
      .flatMap(asynView => asynView.rows.toFuture)
      .map(t => t.value().toString)

  }

  private def getTotalTaxRange(from: Long, to: Long): Future[String] = {

    println(s"getTotalTaxRange $from, $to")
    val query = ViewQuery
      .from("statistic", "tax_range")
      .development(isDevelope)
      .reduce()
      .startKey(from)
      .endKey(to)

    cb.log
      .query(query)
      .flatMap(asynView => asynView.rows.toFuture)
      .map(t => t.value().toString)

  }

  private val errorRecover = {
    val pf: PartialFunction[Throwable, Long] = {
      case e: Exception =>
        e.printStackTrace()
        -1L
    }
    pf
  }

  private def total_log_card(from: Long, to: Long): Try[Long] = Try {
    htDb.withTransaction { implicit conn =>
      val partners = Array("gate_log_card", "hopepay_log_card", "paydirect_log_card", "vdc_log_card", "vtc_log_card")
      partners.foldRight[Long](0L) {
        case (partner: String, sum: Long) =>
          val amoutTotal = SumAmount(partner).on("from" -> from, "to" -> to).as(SumValue).getOrElse(0L)
          logger.error("SumPayVndByDb {} {}", partner, amoutTotal)
          sum + amoutTotal
      }
    }
  }.recover(errorRecover)

  private def total_sms(from: Long, to: Long): Try[Long] = Try {
    db.withTransaction { implicit conn =>
      val amoutTotal = SumAmount("1pay_log").on("from" -> from, "to" -> to).as(SumValue).getOrElse(0L)
      logger.error("SumPayVndByDb {} {}", "1pay_log", amoutTotal)
      amoutTotal
    }
  }.recover(errorRecover)

  private def countNewUser(from: Long, to: Long): Try[Long] = Try {
    db.withTransaction { implicit conn =>
      val newCount = CountNewUser.on("from" -> from, "to" -> to).as(SumValue).getOrElse(0L)
      println("countNewUser {} " + newCount)
      newCount
    }
  }.recover(errorRecover)
}

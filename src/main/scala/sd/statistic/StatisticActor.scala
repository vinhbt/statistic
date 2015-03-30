package sd.statistic

import javax.inject.Inject

import akka.actor.Actor
import akka.event.Logging
import com.couchbase.client.java.document.json.JsonArray
import com.couchbase.client.java.view.{AsyncViewRow, ViewQuery}
import com.sandinh.rx.Implicits._
import sd.cb.CB
import sd.model.PayModel
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.{Success, Failure}
import sd.cb.JsonArrayOps

case class FreeCoin(from: Long, to: Long)

case class TaxEarn(from: Long, to: Long)

case class TaxRange(from: Long, to: Long)

case class SumPayVnd(from: Long, to: Long)

private case class PayRange(month: Int, uid: Int)

case class PayRangeStart(month: Int)

private object PayRangeEnd

class StatisticActor @Inject() (cb: CB) extends Actor {

  private val isDevelope = false

  private val logger = Logging(context.system, this)

  private val PayRangeGetLimit = 50

  private val payRangeList = mutable.Buffer.empty[PayModel]

  override def receive: Receive = {

    case FreeCoin(from: Long, to: Long) =>  getTotalFree(from, to).onComplete{
        case Failure(e) => logger.error("FreeCoin Err {}", e)
        case Success(t) =>
          logger.error(" FreeCoin số user get free_coin 1 lần, 2 lần, 3 lần, 4 lần, 5 lần.")
          logger.error("FreeCoin {} ", t)
      }

    case TaxEarn(from: Long, to: Long) => getTotalTax(from, to).onComplete{
      case Failure(e) => logger.error("TaxEarn Err {}", e)
      case Success(t) =>
        logger.error("Phế sân đình kiếm được: ")
        logger.error("TaxEarn = {}", t)
    }

    case TaxRange(from: Long, to: Long) => getTotalTaxRange(from, to).onComplete{
      case Failure(e) => logger.error("TaxRange Err {}", e)
      case Success(t) =>
        logger.error("Phế sân đình thu được ứng với các mốc <= 500K, 500K < x <= 1M, 1M < x <= 2M, 2M < x <= 5M, > 5M")
        logger.error("TaxRange = {}", t)
    }

    case SumPayVnd(from: Long, to: Long) => sumPayVnd(from, to).onComplete{
      case Failure(e) => logger.error("SumPayVnd Err {}", e)
      case Success(t) => logger.error("Số tiền VND thu được  Nạp thẻ, SMS, tiền mặt, số bảo user nhận được : {} ", t)
    }

    case PayRangeStart(month: Int) =>
      payRangeList.clear()
      self ! PayRange(month, 0)

    case PayRange(month: Int, uid: Int) => paidRange(month, uid)

    case PayRangeEnd => paidRangeEnd()
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

    logger.error("paidRangeEnd Total count = {}, total vnd = {}, total Bao = {}",
      payRangeList.length, payRangeList.foldLeft(0L)(_ + _.n), payRangeList.foldLeft(0L)(_ + _.c))

    logger.error("paidRangeEnd < 20K count = {}, total vnd = {}, total Bao = {}",
      lessThan20k.length, lessThan20k.foldLeft(0L)(_ + _.n), lessThan20k.foldLeft(0L)(_ + _.c))

    logger.error("paidRangeEnd > 20K and < 100K count = {}, total vnd = {}, total Bao = {}",
      from20To100K.length, from20To100K.foldLeft(0L)(_ + _.n), from20To100K.foldLeft(0L)(_ + _.c))

    logger.error("paidRangeEnd > 100K and < 500K count = {}, total vnd = {}, total Bao = {}",
      from100To500K.length, from100To500K.foldLeft(0L)(_ + _.n), from100To500K.foldLeft(0L)(_ + _.c))

    logger.error("paidRangeEnd > 500K and < 1M count = {}, total vnd = {}, total Bao = {}",
      from500To1M.length, from500To1M.foldLeft(0L)(_ + _.n), from500To1M.foldLeft(0L)(_ + _.c))

    logger.error("paidRangeEnd > 1M and < 2M count = {}, total vnd = {}, total Bao = {}",
      from1MTo2M.length, from1MTo2M.foldLeft(0L)(_ + _.n), from1MTo2M.foldLeft(0L)(_ + _.c))

    logger.error("paidRangeEnd > 2M count = {}, total vnd = {}, total Bao = {}",
      from2MTo.length, from2MTo.foldLeft(0L)(_ + _.n), from2MTo.foldLeft(0L)(_ + _.c))

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
      .onComplete{
         case Success(rows) =>
           rows.foreach(row => payRangeList += row)
           if (rows.length > 0) {
             self ! PayRange(month, rows.last.uid)
           }else{
             self ! PayRangeEnd
           }

         case Failure(x) =>
           logger.error("paidRange Err {}", x)
           self ! PayRangeEnd
      }

  }

  private def sumPayVnd(from: Long, to: Long): Future[String] = {
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
      .map(t => t.value().toString)
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
}

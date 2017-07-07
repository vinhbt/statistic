package sd.chiaga

import javax.inject.{ Inject, Singleton }

import com.couchbase.client.java.view.{ AsyncViewRow, ViewQuery }
import play.api.libs.json.{ JsValue, JsBoolean, Json }
import rx.{ Subscriber, Observable }
import rx.Observable.OnSubscribe
import rx.functions.{ Action0, Func0, Func1 }
import sd.cb._

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }

case class ReplayLogData(users: String, logTime: Int, data: String) {
  def toChiaGaData = {
    val uids = {
      val arr = users.split('|')
      arr.take(arr.length / 2).map(_.toInt)
    }

    val dataSplit = data.split('|')
    val z = dataSplit(1).toInt
    val r = dataSplit(2).toInt
    val b = dataSplit(3).toInt
    val bid = (z * 100 + r) * 100 + b
    val stake = dataSplit(6).toInt
    val ga = dataSplit(7).toLong * stake

    new ChiaGaData(uids, ga, logTime, bid)
  }
}
object ReplayLogData { implicit val reader = Json.reads[ReplayLogData] }

class ChiaGaData(val uids: Seq[Int], val ga: Long, val logTime: Int, val bId: Int)

object ChiaGaService {
  final class SFunc1[T1, R](f: T1 => R) extends Func1[T1, R] {
    def call(t1: T1): R = f(t1)
  }
  implicit class FunEx[T1, R](val f: T1 => R) extends AnyVal {
    def rx = new SFunc1(f)
  }
  implicit class FutureEx[T](val f: Future[T]) extends AnyVal {
    def obs: Observable[T] = Observable.create(new OnSubscribe[T] {
      def call(subscriber: Subscriber[_ >: T]): Unit = {
        f.onComplete {
          case Success(v) =>
            subscriber.onNext(v)
            subscriber.onCompleted()
          case Failure(e) =>
            subscriber.onError(e)
            subscriber.onCompleted()
        }
      }
    })
  }
}

@Singleton
class ChiaGaService @Inject() (cb: CB, replayLog: ReplayLog, coinOps: CoinOps) {
  import ChiaGaService._

  private val isDevelope = false

  private val chiaGaMap = TrieMap.empty[Int, ChiaGaData]
  private val uidLogMap = TrieMap.empty[Int, Int]

  private val onNext: AsyncViewRow => Observable[Any] = r => {
    (replayLog.get(r.id) zip cb.log.getJsT[Log](r.id))
      .map {
        case (js: JsValue, l: Log) => js.asOpt[ReplayLogData] match {
          case Some(row) =>
            val d = row.toChiaGaData
            print(s"${d.bId},  ")
            chiaGaMap.get(d.bId) match {
              case None =>
                if (d.ga > 0 && l.m.indexOf("ăn gà") < 0) chiaGaMap += (d.bId -> d)
              case Some(old) if old.logTime < d.logTime =>
                chiaGaMap += (d.bId -> d)
                if (l.m.indexOf("ăn gà") >= 0) chiaGaMap.remove(d.bId)
              case _ => //do nothing
            }
          case None =>
            val create = int36(r.id.substring(2, r.id.indexOf('_')))
            if (l.r == 0) {
              l.d.foreach(dt => if (uidLogMap.getOrElse(dt.head.toInt, 0) < create) {
                uidLogMap.put(dt.head.toInt, create)
              })
            }
        }
      }.recover {
        case e: Throwable =>
          e.printStackTrace()
          println(chiaGaMap.size)
          System.exit(1)
      }
  }.obs

  val onError: Throwable => Observable[Any] = e => {
    e.printStackTrace()
    System.exit(2)
    Observable.just(null)
  }

  val onComplete = new Func0[Observable[_]] {
    override def call(): Observable[_] = {
      println("\n-----\n\nDone!")
      println(chiaGaMap.size)
      Observable.just(null)
    }
  }

  val onCompleteAll = new Action0 {
    def call(): Unit = {
      println("\n-----\n\nDone2!")
      println(s"uidLogMap.size = ${uidLogMap.size}")
      uidLogMap.foreach {
        case (uid, create) =>
          println(s"uid =$uid create = $create")
      }
      println(s"chiaGaMap.size = ${chiaGaMap.size}")
      chiaGaMap.retain((_, d) => {
        d.ga > 0 && !d.uids.forall(uid => uidLogMap.get(uid).nonEmpty && uidLogMap.get(uid).get > d.logTime)
      })
      println(chiaGaMap.size)
      chiaGaMap.foreach {
        case (_, d) => {
          println(s"bid = ${d.bId} ga = ${d.ga} time = ${d.logTime} uids= ${d.uids.mkString(",")}")
          if (d.bId != 50001) {
            val part = d.ga / d.uids.length
            coinOps.change(d.uids, d.bId, notifyCoin = false)(olds => ("Chia lại gà do sự cố server lúc 11h 39p 18 ngày 31-7-2015",
              olds.map(a => a.copy(b = a.b + part)))).map(_ => ())
          }
        }
      }
    }
  }

  def chiaGaWhenErr() = {
    //Chia gàreplay cho Chắn khi bị restart server bất thường
    //1. tạo view từ replaylog trên couchbase:
    //+ emit ra logTime
    //+ chỉ emit với logTime từ trước 10' đến thời điểm restart server
    //2. Tạo 1 Map chiaGaMap từ boardId -> ChiaGaData
    //3. query theo logTime giảm dần, với mỗi viewRow:
    //a. parse d = ChiaGaData
    //b. put d vào chiaGaMap nếu chưa tồn tại d.bId trong chiaGaMap
    //4. Thực hiện chia gà với các value trong chiaGaMap
    val query = ViewQuery
      .from("chiaga", "chiaga")
      .development(isDevelope)

    for (viewRes <- cb.log.query(query)) {
      viewRes.rows()
        .flatMap[Any](
          onNext.rx,
          onError.rx,
          onComplete
        ).doOnCompleted(onCompleteAll)
        .subscribe()
    }
  }
}

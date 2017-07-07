package sd.chiaga

import javax.inject.{ Inject, Singleton }

import com.couchbase.client.java.document.JsonLongDocument
import com.couchbase.client.java.view.{ AsyncViewResult, AsyncViewRow, ViewQuery }
import play.api.Logger
import sd.cb._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.sandinh.rx.Implicits._
import com.sandinh.couchbase.Implicits._

import scala.util.{ Success, Failure }

@Singleton
class FriendName @Inject() (cb: CB, sDAccCao: SDAccCao, sdFriendCao: SDFriendCao) {

  private val logger = Logger(this.getClass)
  private val query = ViewQuery.from("elastic", "fixbug").limit(1000)

  private[this] def row2Data(r: AsyncViewRow): Int =
    r.key.asInstanceOf[Int]

  private def res2Data(res: AsyncViewResult): Future[Seq[Int]] =
    res.rows.scMap(row2Data)
      .fold(ListBuffer.empty[Int])(_ += _)
      .toFuture

  private def doQuery(fromUid: Long): Future[Seq[Int]] = cb.acc
    .query(query.startKey(fromUid))
    .flatMap { res =>
      if (res.success()) {
        if (res.totalRows <= 0) Future successful Seq.empty[Int]
        else res2Data(res)
      } else Future successful Seq.empty[Int]
    }
  def reindex(fromUid: Long): Unit = {
    logger.error(s"reindex failed at $fromUid")
    doQuery(fromUid).onComplete {
      case Failure(e) => logger.error(s"reindex failed at $fromUid", e)
      case Success(data) if data.isEmpty => logger.info(s"done indexing at $fromUid")
      case Success(data) =>
        sdFriendCao.getBulk(data).onComplete {
          case Failure(e) => logger.error(s"insert index failed at $fromUid", e)
          case Success(fs) =>
            val tr = fs zip data
            tr.foreach(fr => if (!fr._1.friends.forall(p => p._2.charAt(0) != '\\') && !fr._1.friends.forall(p => p._2.charAt(0) != '\\')) {
              logger.debug(s" loi  ${fr._2}")
            })
            val nextUid = data.last + 1
            reindex(nextUid)
        }
    }
  }
}

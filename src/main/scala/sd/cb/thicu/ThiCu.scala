package sd.cb.thicu

import java.io.{ File, FileNotFoundException, IOException }
import javax.inject.{ Inject, Singleton }

import akka.event.Logging
import com.couchbase.client.java.view.{ AsyncViewRow, ViewQuery }
import play.api.Logger
import play.mvc.BodyParser.Json
import rx.Observable
import rx.functions.{ Action0, Func0 }
import sd.cb.tour.ThiDinhDataCao
import sd.cb.{ CB, TourAccCao, TourType }
import sd.chiaga.ChiaGaService

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.io.Source

@Singleton
class ThiCu @Inject() (cb: CB, tourAccCao: TourAccCao, thiDinhDataCao: ThiDinhDataCao) {

  import ChiaGaService._

  private val logger = Logger.logger

  private val isDevelope = false

  private val uidLogMap = TrieMap.empty[Int, Int]

  private val onNext: AsyncViewRow => Observable[Unit] = r => {
    val uid = r.key().asInstanceOf[Int]
    println(s"--->$uid")
    //    if (uid > 3173816) tourAccCao.remove(uid)
    Observable.just(null)
    //    tourAccCao.getOrDefault(uid).map { t =>
    //      val hoi = t(Hoi)
    //      tourAccCao.set(uid, t.updated(Hoi, hoi.copy(joinCount = 1))).onComplete{
    //        case Success(_) => println(s"uid ok $uid")
    //        case Failure(_) => println(s"uid faile $uid")
    //      }
    //    }.recover {
    //      case e: Throwable =>
    //        e.printStackTrace()
    //        println("Loi")
    //        System.exit(1)
    //    }
  } //.obs

  val onError: Throwable => Observable[Any] = e => {
    e.printStackTrace()
    System.exit(2)
    Observable.just(null)
  }

  val onComplete = new Func0[Observable[_]] {
    override def call(): Observable[_] = {
      println("\n-----\n\nDone!")
      Observable.just(null)
    }
  }

  val onCompleteAll = new Action0 {
    def call(): Unit = {
      println("\n-----\n\nDone2!")
    }
  }

  def listVongTrong() = {
    uidLogMap.clear()
    thiDinhDataCao.getOrDefault.map { data =>
      println("round=" + data.round)
      Future sequence data.playedUids.map { uid =>
        tourAccCao.getOrDefault(uid).map { t =>
          logger.error(s"$uid -> ${t(TourType.Dinh).priority}  ${t(TourType.Dinh).round}")
          uidLogMap.put(uid, t(TourType.Dinh).priority)
          (uid, t(TourType.Dinh).priority)
        }
      }
    }.foreach { f =>
      f.foreach { g =>
        println("----DONE----")
        //        uidLogMap.toList.sortBy(f => f._2).foreach(u => {
        //
        //        })
        g.sortBy(_._2).foreach { u => println(s"uid = ${u._1}, priority = ${u._2}") }
      }
    }
  }

  def mirgate(fromUid: Int) = {

    val query = ViewQuery
      .from("thicu", "thicu")
      .development(isDevelope)
      .startKey(fromUid)

    for (viewRes <- cb.acc.query(query)) {
      viewRes.rows()
        .flatMap[Any](
          onNext.rx,
          onError.rx,
          onComplete
        ).doOnTerminate(onCompleteAll)
        .subscribe()
    }

    //    for(viewRes <- cb.acc.query(query)) {
    //      viewRes.rows().toList.toFuture.foreach{f => f.asScala.foreach{ u =>
    //        println(u.key().asInstanceOf[Int])
    //      }}
    //    }
  }

  def getListOfFiles(dir: String): List[String] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).map(_.getPath).toList
    } else {
      List[String]()
    }
  }

  def getCurrentDirectory = new java.io.File(".").getCanonicalPath

  case class TourA(uids: Seq[Int], time: String, isR1: Boolean)

  def selectAllUser(dir: String): Unit = {
    val li = mutable.ArrayBuffer.empty[TourA]
    getListOfFiles(dir).foreach(filename =>
      try {
        for (line <- Source.fromFile(filename).getLines()) {
          if (line.length > 0 && line.contains("AddResume 7")) {
            val arr = line.split('|')
            val last = arr(arr.length - 1).trim
            val lastArr = last.split(' ')
            val uids = lastArr(2).trim.split(',').map(_.toInt)
            li.append(TourA(uids, time = arr(1), isR1 = lastArr(1).startsWith("7_0_")))
          }
        }
        li.sortBy(u => u.time).foreach(u =>
          logger.error(s"${u.time} ${u.uids.mkString(",")} ${u.isR1.toString}"))
      } catch {
        case ex: FileNotFoundException => println("Couldn't find that file.")
        case ex: IOException => println("Had an IOException trying to read that file")
      })
    println("!xong")
  }

  def repair(filename: String): Unit = {
    val li = mutable.ArrayBuffer.empty[(Int, Int)]
    try {
      for (line <- Source.fromFile(filename).getLines()) {
        if (line.length > 0) {
          val arr = line.split(' ')
          val uid = arr(0).trim.toInt
          val vong = arr(1).trim.toInt - 1
          li.append((uid, vong))
        }
      }
      li.foreach {
        case (uid, vong) =>
          tourAccCao.getOrDefault(uid).map { t =>
            if (t(TourType.Hoi).round < vong) {
              val data = t.d
              val o1 = data(1).toString
              val o = data(1).copy(round = vong)
              println(s"$uid ${t(TourType.Hoi).round}  $vong ${t.updated(TourType.Hoi, o).toString} $o1")
              println()
              //tourAccCao.set(uid, t.updated(TourType.Hoi, o))
            }
          }
      }
      println("toong so" + li.length)
    } catch {
      case ex: FileNotFoundException => println("Couldn't find that file.")
      case ex: IOException => println("Had an IOException trying to read that file")
    }
    println("!xong")
  }

}

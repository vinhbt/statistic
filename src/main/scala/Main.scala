import java.io.File

import akka.actor._
import com.google.inject.name.Names
import com.google.inject.{ Key, Injector => GuiceInjector }
import com.sandinh.akuice.ActorInject
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.Mode._
import play.api.{ Application, ApplicationLoader, Environment, Play }
import sd.admin._
import sd.cb.thicu.ThiCu
import sd.chiaga.{ ChiaGaService, FriendName }
import sd.mod.ModRequest
import sd.model.GoogleWallet
import sd.statistic._
import sd.util.GoogleWalletRes

import scala.io.Source._

object Main extends App with ActorInject {

  private[this] var app: Application = _

  val hous = 12 * 60 * 60

  val system = ActorSystem("application")

  val dateTime = DateTime.parse("20170301", ISODateTimeFormat.basicDate()).withTimeAtStartOfDay()

  val startTime = 1494955535 - 60 * 60 //DateTime.now().getMillis / 1000 - hous //1494997200 //DateTime.parse("20170304", ISODateTimeFormat.basicDate()).getMillis / 1000

  val stepTime = 24 * 60 * 60 // 1 day

  val endTime = 1494998737 //1494997200 //DateTime.parse("20170312", ISODateTimeFormat.basicDate()).withTimeAtStartOfDay().getMillis / 1000

  protected def injector = app.injector.instanceOf[GuiceInjector]

  private lazy val statisticActor = injectTopActor[StatisticActor]("statistic")

  private lazy val adminActor = injectTopActor[AdminActor]("admin")

  def run(): Unit = {

    println(s"run startTime = $startTime, endTime = $endTime, now = ${DateTime.now().getMillis / 1000}")

    println(injector.getInstance(Key.get(classOf[Int], Names.named("sd.cb.app"))))
    for (ln <- stdin.getLines()) {
      ln match {
        case "c" =>
          println("Connect")
          adminActor ! Connect

        case _ if ln.startsWith("send") =>
          val msg = if (ln.length > 5) ln.substring(5) else ""
          adminActor ! SendMsg(msg)

        case "restart" =>
          adminActor ! Restart("")

        case "config" =>
          adminActor ! UpdateConfig

        case "tour" => adminActor ! UpdateTourCfg
        case "ss" => adminActor ! InsertSession(10, "test3")

        case "adm" =>
          adminActor ! CheckOrCreateAdmin

        case "pay" => statisticActor ! SumPayVnd(startTime, endTime)

        case "free" => statisticActor ! FreeCoin(startTime, endTime)
        case "tax" => statisticActor ! TaxEarn(startTime, endTime)
        case "tax_range" => statisticActor ! TaxRange(startTime, endTime)
        // case "pay_range" => statisticActor ! PayRangeStart(dateTime.getMonthOfYear - 1)
        case "sum" => statisticActor ! SumPayVndByDb(startTime, endTime)
        case "newu" => statisticActor ! NewUserByDb(startTime, endTime)
        case "ga" => injector.getInstance(classOf[ChiaGaService]).chiaGaWhenErr()
        case "name" => injector.getInstance(classOf[FriendName]).reindex(1)
        case "thicu" => injector.getInstance(classOf[ThiCu]).mirgate(1)
        case "vongtrong" =>
          app.injector.instanceOf[ThiCu].listVongTrong()
        case "vip" =>
          injector.getInstance(classOf[Purchase]).vip(startTime, endTime, 50000000)
        case "vipn" =>
          injector.getInstance(classOf[Purchase]).vipNum(startTime, endTime)

        case "newpay" => injector.getInstance(classOf[Purchase]).newUserHasPurchase(startTime, endTime)

        case "wl" =>
          val s = GoogleWallet(3990998, "21", "abc", "GPA.1348-6336-2115-21192", "com.chanpro.Phom", 1476772379L, 0,
            "dnhcjmemjfbmmoaionlboaoe.AO-J1OxxHvMGIpyRbUhn9vtLqu7FSPMOjlDnBVJdZUXc-8ypkXlEX12pKL8BAwGqlvhV0Djat1P03JqYtdMAqWrrpcUFwdgqIwMShfqxz4tCL5eNSrm_U4Q")

          injector.getInstance(classOf[GoogleWalletRes]).validatePayment(s)

        case "cp" =>
          //injector.getInstance(classOf[ModRequest]).genNewPass(1, "vinhbt")
          injector.getInstance(classOf[ModRequest]).updateName(2015, "Lão Ngoan Đồng")

        case "tc" =>
          app.injector.instanceOf[ThiCu].selectAllUser("/Users/vinhbt/Desktop/logs")

        case "rpt" =>
          app.injector.instanceOf[ThiCu].repair("/Users/vinhbt/Desktop/congbao.txt")
        case "y" =>
          injector.getInstance(classOf[Purchase]).allPurChaseLog(startTime, endTime)

        case "x" =>
          println("bye"); System.exit(0)
        case _ =>
      }
    }
  }

  def init(): Unit = {
    val env = Environment(new File("."), getClass.getClassLoader, Prod)
    //    val maybeConf = env.resource("logger.xml")
    //    println(maybeConf.get.getPath)
    val context = ApplicationLoader.createContext(env)
    val loader = ApplicationLoader(context)
    app = loader.load(context)
    Play.start(app)
  }

  init()

  run()
}

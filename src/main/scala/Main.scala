import akka.actor._
import com.google.inject.{Guice, Injector}
import com.sandinh.akuice.ActorInject
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.{SimpleApplication, Play}
import sd.guice.SDModule
import sd.statistic._

import scala.io.Source._


object Main extends App with ActorInject {

  val system = ActorSystem("application")

  val dateTime = DateTime.parse("20150329", ISODateTimeFormat.basicDate()).withTimeAtStartOfDay()

  val startTime = DateTime.parse("20150301", ISODateTimeFormat.basicDate()).getMillis / 1000

  val endTime = DateTime.parse("20150331", ISODateTimeFormat.basicDate()).withTimeAtStartOfDay().getMillis / 1000

  protected var injector: Injector = _

  private lazy val statisticActor = injectTopActor[StatisticActor]("statistic")

  def run(): Unit = {

    println(s"run startTime = $startTime, endTime = $endTime")

    for (ln <- stdin.getLines()) {
      ln match {
        case "free" => statisticActor ! FreeCoin(startTime, endTime)
        case "tax" => statisticActor ! TaxEarn(startTime, endTime)
        case "tax_range" => statisticActor ! TaxRange(startTime, endTime)
        case "pay" => statisticActor ! SumPayVnd(startTime, endTime)
        case "pay_range" => statisticActor ! PayRangeStart(dateTime.getMonthOfYear - 1)
        case _ =>
      }
    }
  }

  def init(): Unit = {
    val cfg = ConfigFactory.load()
    injector = Guice.createInjector(new SDModule(cfg))
    Play.start(new SimpleApplication(cfg))
  }

  init()

  run()
}

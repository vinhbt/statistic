package sd

import com.google.inject.AbstractModule
import org.joda.time.DateTimeZone
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment}

class PlayModule(env: Environment, cfg: Configuration) extends AbstractModule with AkkaGuiceSupport {
  private def init(): Unit = {
    val tz = cfg getString "sd.tz" getOrElse "Asia/Ho_Chi_Minh"
    DateTimeZone setDefault DateTimeZone.forID(tz)

    //verify config. If invalid => throw error before start application
//    for {
//      key <- Seq("sd.extra-file.top-cuoc", "sd.extra-file.notice")
//      filePath <- cfg.getString(key)
//      file = env.rootPath.toPath.resolve(filePath).toFile
//    } assert(file.exists && file.canRead, s"Can't read file $filePath from config $key")
  }

  def configure(): Unit = {
    init()
  }
}

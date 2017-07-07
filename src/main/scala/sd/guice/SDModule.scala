package sd.guice

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import play.api.{ Configuration, Environment }
import play.api.libs.concurrent.AkkaGuiceSupport

class SDModule(env: Environment, cfg: Configuration) extends AbstractModule with AkkaGuiceSupport {
  def configure(): Unit = {
    bind(classOf[Int]).annotatedWith(Names.named("sd.cb.app")).toInstance(cfg.getInt("sd.cb.app").get)
    //    val system = ActorSystem("application")
    //    bind(classOf[ActorSystem]).toInstance(system)

    //bind(classOf[ThiCu]).asEagerSingleton()
  }
}

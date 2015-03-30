package sd.guice

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.typesafe.config.Config

class SDModule(config: Config) extends AbstractModule {
  def configure(): Unit = {
    bind(classOf[Config]).toInstance(config)

    val system = ActorSystem("application", config)
    bind(classOf[ActorSystem]).toInstance(system)
  }
}

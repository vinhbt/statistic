package sd.admin

import javax.inject.{ Inject, Singleton }

import akka.actor.Actor
import akka.event.Logging
import com.sandinh.couchbase.document.CompatStringDocument
import sd.cb._
import sfs2x.client.core.{ BaseEvent, IEventListener, SFSEvent }
import sfs2x.client.SmartFox
import com.smartfoxserver.v2.entities.data.SFSObject
import sd.util.SDCipher
import sfs2x.client.requests.{ ExtensionRequest, LoginRequest, MessageRecipientMode, ModeratorMessageRequest }

object Connect

case class SendMsg(msg: String)

case class Restart(msg: String)

case class InsertSession(uid: Int, msg: String)

object UpdateConfig

object UpdateTourCfg

object CheckOrCreateAdmin

class SFS(val uid: Int, zone: String, sid: String) extends IEventListener {

  val sfsClient = new SmartFox(true)

  sfsClient.addEventListener(SFSEvent.CONNECTION, this)
  sfsClient.addEventListener(SFSEvent.CONNECTION_LOST, this)
  sfsClient.addEventListener(SFSEvent.LOGIN, this)
  sfsClient.addEventListener(SFSEvent.LOGIN_ERROR, this)

  def dispatch(p1: BaseEvent): Unit = {
    //println(s"receive ${p1.getType}")
    p1.getType match {
      case SFSEvent.CONNECTION =>
        val o = new SFSObject
        o.putInt("v", 1000)
        o.putUtfString("s", sid)
        //sfsClient.send(new LoginRequest("84", "", "sfsphom", o))
        sfsClient.send(new LoginRequest(uid.toString, "", zone, o))
      case _ =>
    }
  }
}

object AdminActor {
  val AppIps = Map("chan" -> "42.112.31.46", "phom" -> "123.31.26.26")

  val AppNames = Map(
    "chan" -> "Chắn Pro",
    "phom" -> "Phỏm Pro"
  )
  val sfsNames = Map(
    "chan" -> "sfsak",
    "phom" -> "sfsphom"
  )

  val runasApp = "chan"

  val sfsName = sfsNames(runasApp)

  val Host = AppIps(runasApp) //"123.31.26.26" //"42.112.31.47" //"123.31.26.28" //"42.112.31.46" // "123.31.17.64"//"42.112.31.46" // // // "192.168.1.10"

  val Port = 443

  val HostDeploy = s"$Host:$Port"

  val DefaultMsg = s"${AppNames(runasApp)} xin thông báo, ${AppNames(runasApp)} sẽ tiến hành bảo trì trong 3 phút nữa.<br/>Việc bảo trì sẽ tiến hành trong giây lát.<br/>Rất mong các bạn thông cảm."
  val msg2 = s"Như đã thông báo, ${AppNames(runasApp)} sẽ bảo trì ngay bây giờ.<br/> Việc bảo trì sẽ tiến hành trong 5 phút.<br/>"
}

@Singleton
class AdminActor @Inject() (cbSession: CBSession, sdAccCao: SDAccCao, cb: CB) extends Actor {

  import AdminActor._

  val sfsClient = new SFS(84, sfsName, "RkbmRxpcOKcFmsTMtOTs").sfsClient

  private val logger = Logging(context.system, this)

  private val recipMode = new MessageRecipientMode(MessageRecipientMode.TO_ZONE, null)

  println("TemplateStr=" + SDCipher.encrypt(HostDeploy).get)

  def receive = {

    case CheckOrCreateAdmin => checkOrCreateAdmin()

    case Connect => connect(Host, Port)

    case SendMsg(msg) =>
      val msgSend = if (msg.length == 0) DefaultMsg else msg
      sfsClient.send(new ModeratorMessageRequest(msgSend, recipMode))

    case Restart(msg) =>
      val msgSend = if (msg.length == 0) msg2 else msg
      restart(msgSend)

    case UpdateConfig => updateConfig()

    case UpdateTourCfg => updateTourCfg()

    case InsertSession(uid: Int, session: String) => insertSession(uid, session)
  }

  private def checkOrCreateAdmin() = cb.fodi.upsert(new CompatStringDocument("xfadmin", 84.toString, 0)) //cbSession.get("84")
  //    .recover{
  //      case _: DocumentDoesNotExistException =>
  //        logger.info("DocumentDoesNotExistException")
  cbSession.create(19, "admin")
  //    }

  private def insertSession(uid: Int, session: String) =
    cbSession.create(uid, session)

  private def connect(host: String, port: Int): Unit = {
    println("Connect")
    sfsClient.connect(host, port)
  }

  private def restart(msg: String): Unit = {

    sfsClient.send(new ModeratorMessageRequest(msg, recipMode))

    val o = new SFSObject
    o.putByte("rs", 103)
    sfsClient.send(new ExtensionRequest("admin", o))
    println("restarting...")
  }

  private def updateConfig(): Unit = {
    val o = new SFSObject
    o.putByte("uc", 102)
    sfsClient.send(new ExtensionRequest("admin", o))
    println("updating config...")
  }

  def updateTourCfg(): Unit = {
    val o = new SFSObject
    o.putByte("cfg-tour", 101)
    sfsClient.send(new ExtensionRequest("admin", o))
    println("updating tour...")

  }
}

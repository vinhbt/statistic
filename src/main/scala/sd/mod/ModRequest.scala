package sd.mod

import java.sql.Connection
import javax.inject.{ Inject, Singleton }

import anorm.SqlParser._
import anorm._
import com.sandinh.phputils.PhpObject
import play.api.db.{ Database, _ }
import sd.cb.{ CB, SDAccCao }
import sd.util.Romanize

import scala.util.Random

@Singleton
class ModRequest @Inject() (cb: CB, sDAccCao: SDAccCao, @NamedDatabase("ht") htDb: Database, db: Database) {

  private val SqlCheckName = SQL("SELECT user_id FROM xf_user WHERE username = {name} OR username = {romanized}")

  private val SqlUpdateName = SQL("UPDATE xf_user SET username = {name} WHERE user_id = {uid}")

  private val SqlUpdatePass = SQL("UPDATE xf_user_authenticate SET data = {data}, remember_key = {remember} WHERE user_id = {uid}")

  private def checkUserExist(username: String)(implicit conn: Connection): Int = {
    val count = SqlCheckName
      .on('name -> username, 'romanized -> Romanize(username))
      .as(scalar[Int].singleOpt)
    count.getOrElse(0)
  }

  def updateName(uid: Int, newName: String) = {
    println("updateName " + uid + " " + newName + " " + Romanize(newName))
    db.withConnection { implicit conn =>
      if (checkUserExist(newName) != 0) {
        println("updateName " + uid + " " + newName + " " + Romanize(newName))
        SqlUpdateName.on("uid" -> uid, "name" -> newName).executeUpdate()
        sDAccCao.change(uid) { u => u.map(u1 => u1.copy(n = newName)).orNull }
        println("updateName " + uid + " " + newName)
      }
    }
  }

  private def sqlUpdatePass(uid: Int, passData: String, remember: String) = db.withConnection { implicit conn =>
    SqlUpdatePass.on("uid" -> uid, "data" -> passData, "remember" -> remember).executeInsert()
  }

  def genNewPass(uid: Int, newPass: String): String = {
    val salt = Random.alphanumeric.take(16).mkString
    val remember = Random.alphanumeric.take(40).mkString
    val hashedPass = SDAuthentication.pbkdf2(newPass, salt)
    val passData = PhpObject.stringify(Map("hash" -> hashedPass, "salt" -> salt))
    sqlUpdatePass(uid, passData, remember)
    newPass
  }

  def moveNick(fromUid: Int, toUid: Int) = {

  }
}

package sd.mod

import javax.inject.{ Inject, Singleton }

import anorm.SqlParser._
import anorm._
import io.github.nremond.PBKDF2
import org.apache.commons.codec.binary.Hex.encodeHexString
import play.api.db.Database

import scala.io.Codec.UTF8

@Singleton
class SDAuthentication @Inject() (db: Database) {
  private val ParserCheckName = (long("user_id") ~ str("gender") ~ get[Option[String]]("user_reason") ~ get[Option[Int]]("end_date")).map {
    case a ~ b ~ c ~ d => (a, b, c.orNull, d.getOrElse(-1))
  }.singleOpt
  private val SqlCheckName = SQL("SELECT u.user_id, u.gender, b.user_reason, b.end_date FROM xf_user u LEFT JOIN xf_user_ban b ON u.user_id = b.user_id WHERE u.username = {user}")
  private val SqlCheckPass = SQL("SELECT data FROM xf_user_authenticate WHERE user_id = {uid}")
  private val SqlUpdatePass = SQL("UPDATE xf_user_authenticate SET data = {data} WHERE user_id = {uid}")

  //  def checkUser(username: String) = db.withConnection { implicit c =>
  //    SqlCheckName.
  //      on("user" -> username).
  //      as(ParserCheckName).
  //      getOrElse(throw new UserNotFoundException)
  //  }
  //
  //  /** @throws UnauthorizedException when the provided password not ok
  //   *  @throws Exception when user not exist or some other error occur
  //   */
  //  def checkPass(uid: Int, pw: String): Unit = db.withConnection { implicit c =>
  //    val raw = SqlCheckPass.on("uid" -> uid).as(ParserCheckPass)
  //    val stored = PhpObject.parse(raw).asInstanceOf[Map[String, String]]
  //    val hash = stored("hash")
  //    val salt = stored("salt")
  //    val hashedPass = pbkdf2(pw, salt)
  //    if (hash.length > 20 || hash.contains("-")) {
  //      if (hash != accidentalPbkdf2(pw, salt)) throw new UnauthorizedException
  //      SqlUpdatePass.on("uid" -> uid, "data" -> PhpObject.stringify(stored.updated("hash", hashedPass))).executeUpdate()
  //    } else if (hash != hashedPass) throw new UnauthorizedException
  //  }
}

object SDAuthentication {
  //old style PBKDF2 in pbkdf2-scala 0.2
  @inline def pbkdf2(pw: String, salt: String) = encodeHexString(hashing(pw, salt))

  //Hàm này được dùng cho user đăng ký account trên mobile từ đêm 28 đến tối 29/5/2014
  @inline private def accidentalPbkdf2(pw: String, salt: String) = hashing(pw, salt).mkString

  @inline private def hashing(pw: String, salt: String) =
    PBKDF2(pw.getBytes(UTF8.charSet), salt.getBytes(UTF8.charSet), 1000, 10, "HmacSHA256")
}

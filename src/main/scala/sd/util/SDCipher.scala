package sd.util

import org.apache.commons.codec.binary.Base64.{ decodeBase64, encodeBase64String }
import javax.crypto.Cipher
import javax.crypto.Cipher.{ DECRYPT_MODE, ENCRYPT_MODE }
import javax.crypto.spec.SecretKeySpec

import scala.io.Codec.UTF8.{ charSet => UTF8 }
import scala.util.Try

object SDCipher {
  private val sdKey = new SecretKeySpec(decodeBase64("AQIDBAUGBWGJCGSMDQ0PAA=="), "AES")
  private def cipher(mode: Int) = {
    val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
    c.init(mode, sdKey)
    c
  }

  /** @note this method do NOT {{{ replace('/', '_') }}} */
  def encrypt(s: String): Try[String] = Try {
    encodeBase64String(cipher(ENCRYPT_MODE).doFinal(s.getBytes(UTF8)))
  }

  /**
   * to avoid / character in url, client replaces '/' by '_'
   *  we need to change back when decode
   */
  def decrypt(data: String): Try[String] = Try {
    val s = data.replace('_', '/')
    new String(cipher(DECRYPT_MODE).doFinal(decodeBase64(s)), UTF8)
  }
}

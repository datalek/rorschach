package rorschach.util

import org.specs2.mutable.Specification

class DefaultCryptoSpec extends Specification {

  val textToEncrypt = "&%%ASDIIJPOppokasojdpOWIJD"
  val key = "hello encryption!!!" //"this is my key"
  val crypto = Crypto

  "Crypto" should {
    "encode and decode a string without error" >> {
      val encoded = crypto.encrypt(key, textToEncrypt)
      val decoded = crypto.decrypt(key, encoded)
      decoded must be equalTo textToEncrypt
    }
  }
}

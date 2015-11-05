package rorschach.util

import org.specs2.mutable.Specification

class BCryptPasswordHasherSpec extends Specification {

  val password = "this is a password to hash :O @#¶]["
  val passwordHasher = new BCryptPasswordHasher

  "the hash method" should {
    "hash a password without errors" >> {
      val passwordInfo = passwordHasher.hash(password)
      passwordInfo.hasher must be equalTo passwordHasher.id
    }
  }

  "the match method" should {
    "hash a password and then match it without errors" >> {
      val passwordInfo = passwordHasher.hash(password)
      passwordHasher.matches(passwordInfo, password) should be equalTo true
    }

    "return false if password doesn't match" >> {
      val passwordInfo = passwordHasher.hash(password)
      passwordHasher.matches(passwordInfo, s"other password :O - ${scala.math.random}") should be equalTo false
    }
  }

}

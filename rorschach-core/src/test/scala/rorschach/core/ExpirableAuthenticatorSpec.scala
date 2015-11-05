package rorschach.core

import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

class ExpirableAuthenticatorSpec extends Specification with NoTimeConversions {

  case class DummyAuthenticator(
    loginInfo: LoginInfo = null,
    lastUsedDateTime: DateTime,
    expirationDateTime: DateTime,
    idleTimeout: Option[FiniteDuration]
  ) extends ExpirableAuthenticator {
    override type Value = Unit
  }

  "isValid method" should {
    "return false if authenticator is expired" in {
      val expired = DummyAuthenticator(
        lastUsedDateTime = DateTime.now.minusSeconds(2),
        expirationDateTime = DateTime.now.minusSeconds(1),
        idleTimeout = null)
      expired.isValid should beFalse
    }
    "return false if authenticator is timed out" >> {
      val timedOut = DummyAuthenticator(
        lastUsedDateTime = DateTime.now.minusSeconds(1),
        expirationDateTime = DateTime.now.plusDays(1),
        idleTimeout = Some(1 second))
      timedOut.isValid should beFalse
    }
    "return true if authenticator isn't timed out and isn't expired" >> {
      val valid = DummyAuthenticator(
        lastUsedDateTime = DateTime.now.minusSeconds(1),
        expirationDateTime = DateTime.now.plusDays(1),
        idleTimeout = Some(1 minute))
      valid.isValid should beTrue
    }
  }

}

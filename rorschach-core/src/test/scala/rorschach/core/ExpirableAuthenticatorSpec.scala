package rorschach.core

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

class ExpirableAuthenticatorSpec extends Specification with NoTimeConversions {

  case class DummyAuthenticator(
    loginInfo: LoginInfo = null,
    lastUsedDateTime: Instant,
    expirationDateTime: Instant,
    idleTimeout: Option[FiniteDuration]
  ) extends ExpirableAuthenticator

  "isValid method" should {
    "return false if authenticator is expired" in {
      val expired = DummyAuthenticator(
        lastUsedDateTime = Instant.now.minusSeconds(2),
        expirationDateTime = Instant.now.minusSeconds(1),
        idleTimeout = null
      )
      expired.isValid should beFalse
    }
    "return false if authenticator is timed out" >> {
      val timedOut = DummyAuthenticator(
        lastUsedDateTime = Instant.now.minusSeconds(1),
        expirationDateTime = Instant.now.plus(1, ChronoUnit.DAYS),
        idleTimeout = Some(1.second)
      )
      timedOut.isValid should beFalse
    }
    "return true if authenticator isn't timed out and isn't expired" >> {
      val valid = DummyAuthenticator(
        lastUsedDateTime = Instant.now.minusSeconds(1),
        expirationDateTime = Instant.now.plus(1, ChronoUnit.DAYS),
        idleTimeout = Some(1 minute)
      )
      valid.isValid should beTrue
    }
  }

}

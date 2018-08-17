package rorschach.jwt.services

import rorschach.jwt._
import rorschach.util._
import rorschach.core._
import rorschach.core.daos._
import rorschach.exceptions._
import java.time.Instant
import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent._
import scala.concurrent.duration._
import test.util._

class JwtAuthenticatorServiceSpec extends Specification with NoTimeConversions {

  "the create method" should {
    "return authenticator with generated id" >> new Context {
      val (id, now) = ("random", Instant.now)
      (idGenerator.generate _).expects().returns(Future.successful(id))
      (clock.now _).expects().returns(now)
      val result = await(authenticatorService.create(loginInfo))
      result.id must be equalTo id
      result.expirationDateTime must be equalTo now.plusMillis(settings.authenticatorExpiry.toMillis)
      result.lastUsedDateTime must be equalTo now
      result.idleTimeout must be equalTo settings.authenticatorIdleTimeout
      result.loginInfo must be equalTo loginInfo
    }
    "throw AuthenticatorCreationException if something go wrong" >> new Context {
      (idGenerator.generate _).expects().returns(Future.failed(new Exception("explosion during generation!")))
      await(authenticatorService.create(loginInfo)) should throwA[AuthenticatorCreationException]
    }
  }

  "the create method" should {
    "return authenticator after store it on database" >> new Context {
      (dao.add _).expects(authenticator).returns(Future.successful(authenticator))
      val result = await(authenticatorService.init(authenticator))
    }
    "return authenticator without store it on database" >> new Context {
      val result = await(authenticatorServiceWithoutDao.init(authenticator))
    }
  }

  "the update method" should {
    "return authenticator untouched and store it" >> new Context {
      (dao.update _).expects(authenticator).returns(Future.successful(authenticator))
      await(authenticatorService.update(authenticator))
    }
    "return authenticator untouched and don't store it with no dao set" >> new Context {
      await(authenticatorServiceWithoutDao.update(authenticator))
    }
    "throw AuthenticatorCreationException if something go wrong" >> new Context {
      (dao.update _).expects(authenticator).returns(Future.failed(new Exception("explosion during store action!")))
      await(authenticatorService.update(authenticator)) should throwA[AuthenticatorUpdateException]
    }
  }

  "the thouch method" should {
    "return authenticator touched" >> new Context {
      val now = Instant.now
      (clock.now _).expects().returns(now)
      authenticatorService.touch(authenticator) should beRight[JwtAuthenticator].like {
        case a => a.lastUsedDateTime must be equalTo now
      }
    }
    "return authenticator untouched" >> new Context {
      val withoutIdleTimeout = authenticator.copy(idleTimeout = None)
      authenticatorService.touch(withoutIdleTimeout) should beLeft
    }
  }

  "the remove method" should {
    "return authenticator and remove it from store" >> new Context {
      (dao.remove _).expects(authenticator.id).returns(Future.successful(authenticator))
      await(authenticatorService.remove(authenticator)) should be equalTo authenticator
    }
    "return authenticator untouched and don't store it with no dao set" >> new Context {
      await(authenticatorServiceWithoutDao.remove(authenticator))
    }
    "throw AuthenticatorCreationException if something go wrong" >> new Context {
      (dao.remove _).expects(authenticator.id).returns(Future.failed(new Exception("explosion during store action!")))
      await(authenticatorService.remove(authenticator)) should throwA[AuthenticatorDiscardingException]
    }
  }

  "the renew method" should {
    "return authenticator with generated id" >> new Context {
      val (id, now) = ("random", Instant.now)
      (dao.remove _).expects(authenticator.id).returns(Future.successful(authenticator))
      (idGenerator.generate _).expects().returns(Future.successful(id))
      (clock.now _).expects().returns(now)
      val result = await(authenticatorService.renew(authenticator))
      result.id must be equalTo id
      result.lastUsedDateTime must be equalTo now
    }
    "return authenticator with generated id withoud storing it to store if no dao is set" >> new Context {
      val (id, now) = ("random", Instant.now)
      (idGenerator.generate _).expects().returns(Future.successful(id))
      (clock.now _).expects().returns(now)
      val result = await(authenticatorServiceWithoutDao.renew(authenticator))
      result.id must be equalTo id
      result.lastUsedDateTime must be equalTo now
    }
    "throw AuthenticatorRenewalException if something go wrong" >> new Context {
      (dao.remove _).expects(authenticator.id).returns(Future.failed(new Exception("explosion during store action!")))
      await(authenticatorService.renew(authenticator)) should throwA[AuthenticatorRenewalException]
    }
  }

  trait Context extends MockContext with Common {
    import scala.concurrent.ExecutionContext.Implicits._
    val loginInfo = LoginInfo("provider", "this is identificator of user")
    val authenticator = JwtAuthenticator(
      id = "identificator",
      loginInfo = loginInfo,
      lastUsedDateTime = Instant.now,
      expirationDateTime = Instant.now,
      idleTimeout = Some(5.minutes)
    )
    val settings = JwtAuthenticatorSettings(
      issuerClaim = "this is my issuer claim",
      authenticatorIdleTimeout = Some(1.minute),
      authenticatorExpiry = 1.hour,
      sharedSecret = "this-is-my-very-secret-key-yep-man-yep"
    )
    /* create mock */
    val idGenerator = mock[IdGenerator]
    val dao = mock[AuthenticatorDao[JwtAuthenticator]]
    val clock = mock[Clock]
    /* subjects under tests */
    val authenticatorService = new JwtAuthenticatorService(idGenerator, settings, Some(dao), clock)
    val authenticatorServiceWithoutDao = new JwtAuthenticatorService(idGenerator, settings, dao = None, clock)
  }

}

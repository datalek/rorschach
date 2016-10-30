package rorschach.core.authenticators

import org.joda.time.DateTime
import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import rorschach.core.LoginInfo
import rorschach.core.daos.AuthenticatorDao
import rorschach.exceptions._
import rorschach.util.{Base64, Clock, IdGenerator}
import test.util.Common
import scala.concurrent.Future
import scala.concurrent.duration._
import CookieAuthenticator._

class CookieAuthenticatorSpec extends Specification with Common with NoTimeConversions {

  "the create method" should {
    "return authenticator with generated id" >> new Context {
      val (id, now) = ("random", DateTime.now)
      (idGenerator.generate _).expects().returns(Future.successful(id))
      (clock.now _).expects().returns(now)
      val result = await(authenticatorService.create(loginInfo))
      result.id must be equalTo id
      result.expirationDateTime must be equalTo now.plusMillis(settings.authenticatorExpiry.toMillis.toInt)
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
      val now = DateTime.now
      (clock.now _).expects().returns(now)
      authenticatorService.touch(authenticator) should beRight[CookieAuthenticator].like {
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
      val (id, now) = ("random", DateTime.now)
      (dao.remove _).expects(authenticator.id).returns(Future.successful(authenticator))
      (idGenerator.generate _).expects().returns(Future.successful(id))
      (clock.now _).expects().returns(now)
      val result = await(authenticatorService.renew(authenticator))
      result.id must be equalTo id
      result.lastUsedDateTime must be equalTo now
    }
    "return authenticator with generated id withoud storing it to store if no dao is set" >> new Context {
      val (id, now) = ("random", DateTime.now)
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

  "The `deserialize` method of the authenticator" should {
    "throw an AuthenticatorException if the given value can't be parsed as Json" in new Context {
      val value = "invalid"
      deserialize(value)(settings) must beFailedTry.withThrowable[AuthenticatorException]
    }
    "throw an AuthenticatorException if the given value is in the wrong Json format" in new Context {
      val value = Base64.encode("{\"a\": \"test\"}")
      deserialize(value)(settings) must beFailedTry.withThrowable[AuthenticatorException]
    }
  }

  "The `serialize/deserialize` method of the authenticator" should {
    "handle an encrypted authenticator" in new Context {
      val s = settings.copy(encryptAuthenticator = true)
      val value = serialize(authenticator)(s)
      deserialize(value)(s) must beSuccessfulTry.withValue(authenticator)
    }
    "handle an unencrypted authenticator" in new Context {
      val s = settings.copy(encryptAuthenticator = false)
      val value = serialize(authenticator)(s)
      deserialize(value)(s) must beSuccessfulTry.withValue(authenticator)
    }
  }

  "the serialize method" should {
    "serialize authenticator without error" >> new Context {
      await(authenticatorService.serialize(authenticator)) must be equalTo serialize(authenticator)(settings)
    }
  }

  "the deserialize method" should {
    "return the token deserialized" >> new Context {
      val value = serialize(authenticator)(settings)
      await(authenticatorServiceWithoutDao.deserialize(value)) must beSome(authenticator)
    }
    "return the token deserialized with store lookup" >> new Context {
      val value = serialize(authenticator)(settings)
      (dao.find _).expects(authenticator.id).returns(Future.successful(Option(authenticator)))
      await(authenticatorService.deserialize(value)) must beSome(authenticator)
    }
    "return None if the authenticator was not found in store" >> new Context {
      val value = serialize(authenticator)(settings)
      (dao.find _).expects(authenticator.id).returns(Future.successful(None))
      await(authenticatorService.deserialize(value)) must beNone
    }
    "throw AuthenticatorException if no authentication was found" >> new Context {
      val value = Base64.encode("{\"a\": \"test\"}")
      await(authenticatorService.deserialize(value)) must throwA[AuthenticatorException]
    }
  }

  trait Context extends MockContext {
    val loginInfo = LoginInfo("provider", "this is identificator of user")
    val authenticator = CookieAuthenticator(
      id = "identificator",
      loginInfo = loginInfo,
      lastUsedDateTime = DateTime.now,
      expirationDateTime = DateTime.now,
      idleTimeout = Some(5.minutes),
      cookieMaxAge = Some(10.minutes)
    )

    /* generate mock */
    val idGenerator = mock[IdGenerator]
    val dao = mock[AuthenticatorDao[CookieAuthenticator]]
    val clock = mock[Clock]
    val settings = CookieAuthenticatorSettings(
      cookieName = "myCookie",
      cookiePath ="/",
      cookieDomain = None,
      secureCookie = true,
      httpOnlyCookie = true,
      encryptAuthenticator = true,
      useFingerprinting = true,
      cookieMaxAge = Some(10.minutes),
      authenticatorIdleTimeout = Some(5.minutes),
      authenticatorExpiry = 12.hours
    )

    /* subjects under tests */
    val authenticatorService = new CookieAuthenticatorService(idGenerator, settings, Some(dao), clock)
    val authenticatorServiceWithoutDao = new CookieAuthenticatorService(idGenerator, settings, dao = None, clock)
  }

}

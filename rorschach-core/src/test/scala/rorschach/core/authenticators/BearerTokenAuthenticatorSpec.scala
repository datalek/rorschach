package rorschach.core.authenticators

import org.joda.time.DateTime
import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import rorschach.core.LoginInfo
import rorschach.core.daos.AuthenticatorDao
import rorschach.exceptions._
import rorschach.util.{Clock, IdGenerator}
import test.util.Common
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class BearerTokenAuthenticatorSpec extends Specification with Common with NoTimeConversions {

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
  }

  "the update method" should {
    "return authenticator untouched and store it" >> new Context {
      (dao.update _).expects(authenticator).returns(Future.successful(authenticator))
      await(authenticatorService.update(authenticator))
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
      authenticatorService.touch(authenticator) should beRight[BearerTokenAuthenticator].like {
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
    "throw AuthenticatorRenewalException if something go wrong" >> new Context {
      (dao.remove _).expects(authenticator.id).returns(Future.failed(new Exception("explosion during store action!")))
      await(authenticatorService.renew(authenticator)) should throwA[AuthenticatorRenewalException]
    }

    "the serialize method" should {
      "serialize as id simple token" >> new Context {
        await(authenticatorService.serialize(authenticator)) must be equalTo authenticator.id
      }
    }

    "the deserialize method" should {
      "return the token deserialized" >> new Context {
        (dao.find _).expects(authenticator.id).returns(Future.successful(Option(authenticator)))
        await(authenticatorService.deserialize(authenticator.id)) must be equalTo authenticator
      }
      "throw AuthenticatorException if no authentication was found" >> new Context {
        (dao.find _).expects(authenticator.id).returns(Future.successful(None))
        await(authenticatorService.deserialize(authenticator.id)) must throwA[AuthenticatorException]
      }
      "throw Exception if dao return an exception" >> new Context {
        (dao.find _).expects(authenticator.id).returns(Future.failed(new Exception("Error message")))
        await(authenticatorService.deserialize(authenticator.id)) must throwA[Exception]
      }
    }
  }

  trait Context extends MockContext {
    val loginInfo = LoginInfo("provider", "this is identificator of user")
    val authenticator = BearerTokenAuthenticator(
      id = "identificator",
      loginInfo = loginInfo,
      lastUsedDateTime = DateTime.now,
      expirationDateTime = DateTime.now,
      idleTimeout = Some(5.minutes)
    )

    /* generate mock */
    val idGenerator = mock[IdGenerator]
    val dao = mock[AuthenticatorDao[BearerTokenAuthenticator]]
    val clock = mock[Clock]
    val settings = BearerTokenAuthenticatorSettings(
      headerName = "",
      authenticatorIdleTimeout = Some(1.minute),
      authenticatorExpiry = 1.hour
    )

    /* subjects under tests */
    val authenticatorService = new BearerTokenAuthenticatorService(idGenerator, settings, dao, clock)
  }
}
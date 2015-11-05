package rorschach.core.authenticators


import org.joda.time.DateTime
import org.scalamock.specs2.MockContext
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import play.api.libs.json._
import rorschach.core.LoginInfo
import rorschach.core.daos.AuthenticatorDao
import rorschach.exceptions._
import rorschach.util.{Crypto, Clock, IdGenerator, Base64}
import test.util.Common
import scala.concurrent.Future
import scala.concurrent.duration._
import JWTAuthenticator._

class JWTAuthenticatorSpec extends Specification with JsonMatchers with Common with NoTimeConversions {

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
      authenticatorService.touch(authenticator) should beLeft[JWTAuthenticator].like {
        case a => a.lastUsedDateTime must be equalTo now
      }
    }
    "return authenticator untouched" >> new Context {
      val withoutIdleTimeout = authenticator.copy(idleTimeout = None)
      authenticatorService.touch(withoutIdleTimeout) should beRight
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

  "The `serialize` method of the authenticator" should {
    "return a JWT with an expiration time" in new Context {
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))
      json must /("exp" -> (authenticator.expirationDateTime.getMillis / 1000).toInt)
    }

    "return a JWT with an encrypted subject" in new Context {
      val s = settings.copy(encryptSubject = true)
      val jwt = serialize(authenticator)(s)
      val json = Json.parse(Base64.decode(jwt.split('.').apply(1)))
      val sub = Json.parse(Crypto.decrypt("", (json \ "sub").as[String])).as[LoginInfo]
      sub must be equalTo authenticator.loginInfo
    }.pendingUntilFixed("implementation of Crypto")

    "return a JWT with an unencrypted subject" in new Context {
      val s = settings.copy(encryptSubject = false)
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.')(1))
      json must /("sub" -> Base64.encode(Json.toJson(authenticator.loginInfo)))
    }

    "return a JWT with an issuer" in new Context {
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))
      json must /("iss" -> settings.issuerClaim)
    }

    "return a JWT with an issued-at time" in new Context {
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))
      json must /("iat" -> (authenticator.lastUsedDateTime.getMillis / 1000).toInt)
    }

    "throw an AuthenticatorException if a reserved claim will be overridden" in new Context {
      val claims = Map("jti" -> "reserved")
      serialize(authenticator.copy(customClaims = Some(claims)))(settings) must throwA[AuthenticatorException]
    }

    "throw an AuthenticatorException if an unexpected value was found in the arbitrary claims" in new Context {
      val claims = Map("null" -> JsNull)
      serialize(authenticator.copy(customClaims = Some(claims)))(settings) must throwA[AuthenticatorException]
    }

    "return a JWT with arbitrary claims" in new Context {
      val jwt = serialize(authenticator.copy(customClaims = Some(customClaims)))(settings)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("boolean" -> true)
      json must /("string" -> "string")
      json must /("number" -> 1234567890)
      json must /("array") /# 0 / 1
      json must /("array") /# 1 / 2
      json must /("object") / "array" /# 0 / "string1"
      json must /("object") / "array" /# 1 / "string2"
      json must /("object") / "object" / "array" /# 0 / "string"
      json must /("object") / "object" / "array" /# 1 / false
      json must /("object") / "object" / "array" /# 2 / ("number" -> 1)
    }
  }

  "The `unserialize` method of the authenticator" should {
    "throw an AuthenticatorException if the given token can't be parsed" in new Context {
      val jwt = "invalid"
      unserialize(jwt)(settings) must beFailedTry.withThrowable[AuthenticatorException]
    }

    "throw an AuthenticatorException if the given token couldn't be verified" in new Context {
      val jwt = serialize(authenticator)(settings) + "-wrong-sig"
      unserialize(jwt)(settings) must beFailedTry.withThrowable[AuthenticatorException]
    }

    "throw an AuthenticatorException if encrypted token gets serialized unencrypted" in new Context {
      val s0 = settings.copy(encryptSubject = true)
      val jwt = serialize(authenticator)(s0)
      val s1 = settings.copy(encryptSubject = false)
      unserialize(jwt)(s1) must beFailedTry.withThrowable[AuthenticatorException]
    }.pendingUntilFixed("implementation of Crypto")

    "unserialize a JWT with an encrypted subject" in new Context {
      val s = settings.copy(encryptSubject = true)
      val jwt = serialize(authenticator)(s)
      unserialize(jwt)(settings) must beSuccessfulTry.withValue(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      ))
    }.pendingUntilFixed("implementation of Crypto")

    "unserialize a JWT with an encrypted subject" in new Context {
      val s = settings.copy(encryptSubject = false)
      val jwt = serialize(authenticator)(s)
      unserialize(jwt)(settings) must beSuccessfulTry.withValue(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      ))
    }.pendingUntilFixed("implementation of Crypto")

    "unserialize a JWT with arbitrary claims" in new Context {
      val s = settings.copy(encryptSubject = false)
      val jwt = serialize(authenticator.copy(customClaims = Some(customClaims)))(settings)
      unserialize(jwt)(settings) must beSuccessfulTry.like {
        case a => a.customClaims must beSome(customClaims)
      }
    }

    "throw an AuthenticatorException if an unexpected value was found in the arbitrary claims" in new Context {
      val claims = Map("null" -> JsNull)
      serialize(authenticator.copy(customClaims = Some(claims)))(settings) must throwA[AuthenticatorException]
    }
  }

  trait Context extends MockContext {
    val loginInfo = LoginInfo("provider", "this is identificator of user")
    val authenticator = JWTAuthenticator(
      id = "identificator",
      loginInfo = loginInfo,
      lastUsedDateTime = DateTime.now,
      expirationDateTime = DateTime.now,
      idleTimeout = Some(5.minutes)
    )

    /* generate mock */
    val idGenerator = mock[IdGenerator]
    val dao = mock[AuthenticatorDao[JWTAuthenticator]]
    val clock = mock[Clock]
    val settings = JWTAuthenticatorSettings(
      headerName = "",
      issuerClaim = "this is my issuer claim",
      authenticatorIdleTimeout = Some(1.minute),
      authenticatorExpiry = 1.hour,
      sharedSecret = "this is my very secret key"
    )

    lazy val customClaims = Map(
      "boolean" -> true,
      "string" -> "string",
      "number" -> 1234567890,
      "array" -> List(1, 2),
      "object" -> Map(
        "array" -> Seq("string1", "string2"),
        "object" -> Map(
          "array" -> List("string", false, Map("number" -> 1))
        )
      )
    )

    /* subjects under tests */
    val authenticatorService = new JWTAuthenticatorService(idGenerator, settings, Some(dao), clock)
    val authenticatorServiceWithoutDao = new JWTAuthenticatorService(idGenerator, settings, dao = None, clock)
  }

}
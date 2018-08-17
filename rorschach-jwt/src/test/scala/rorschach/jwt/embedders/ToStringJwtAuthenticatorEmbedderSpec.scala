package rorschach.jwt.embedders

import java.time.Instant
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import play.api.libs.json._
import rorschach.core.LoginInfo
import rorschach.exceptions._
import rorschach.jwt._
import rorschach.util._
import scala.concurrent.duration._

class ToStringJwtAuthenticatorEmbedderSpec extends Specification with JsonMatchers {

  "The `serialize` method of the authenticator" should {
    "return a JWT with an expiration time" in new Context {
      val jwt = serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))
      json must /("exp" -> (authenticator.expirationDateTime.getEpochSecond).toInt)
    }

    "return a JWT with an encrypted subject" in new Context {
      val s = settings.copy(encryptKey = Some("the key secret!"))
      val jwt = serialize.copy(settings = s)(authenticator)
      val json = Json.parse(Base64.decode(jwt.split('.').apply(1)))
      val sub = Json.parse(Crypto.decrypt("", (json \ "sub").as[String])).as[LoginInfo]
      sub must be equalTo authenticator.loginInfo
    } //.pendingUntilFixed("implementation of Crypto")

    "return a JWT with an unencrypted subject" in new Context {
      val s = settings.copy(encryptKey = None)
      val jwt = serialize(authenticator)
      val json = Base64.decode(jwt.split('.')(1))
      json must /("sub" -> Base64.encode(Json.toJson(authenticator.loginInfo).toString))
    }

    "return a JWT with an issuer" in new Context {
      val jwt = serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))
      json must /("iss" -> settings.issuerClaim)
    }

    "return a JWT with an issued-at time" in new Context {
      val jwt = serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))
      json must /("iat" -> (authenticator.lastUsedDateTime.getEpochSecond).toInt)
    }

    "throw an AuthenticatorException if a reserved claim will be overridden" in new Context {
      val claims = Map("jti" -> "reserved")
      serialize(authenticator.copy(customClaims = Some(claims))) must throwA[AuthenticatorException]
    }

    "throw an AuthenticatorException if an unexpected value was found in the arbitrary claims" in new Context {
      val claims = Map("null" -> JsNull)
      serialize(authenticator.copy(customClaims = Some(claims))) must throwA[AuthenticatorException]
    }

    "return a JWT with arbitrary claims" in new Context {
      val jwt = serialize(authenticator.copy(customClaims = Some(customClaims)))
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

  trait Context extends Scope {
    implicit val loginInfoFormat = JwtAuthenticatorFormat.jsonFormat
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

    val serialize = ToStringJwtAuthenticatorEmbedder(settings)
  }

}


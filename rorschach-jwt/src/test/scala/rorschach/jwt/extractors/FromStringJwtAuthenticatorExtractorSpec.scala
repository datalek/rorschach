package rorschach.jwt.extractors

import org.joda.time.DateTime
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import play.api.libs.json._
import rorschach.core.LoginInfo
import rorschach.exceptions._
import rorschach.jwt._
import rorschach.jwt.embedders.ToStringJwtAuthenticatorEmbedder
import test.util.Common
import scala.concurrent.duration._

class FromStringJwtAuthenticatorExtractorSpec extends Specification with JsonMatchers with NoTimeConversions {

  "The `deserialize` method of the authenticator" should {
    "throw an AuthenticatorException if the given token can't be parsed" in new Context {
      val jwt = "invalid"
      await(deserialize(jwt)) must throwA[AuthenticatorException]
    }

    "throw an AuthenticatorException if the given token couldn't be verified" in new Context {
      val jwt = serialize(authenticator) + "-wrong-sig"
      await(deserialize(jwt)) must throwA[AuthenticatorException]
    }

    "throw an AuthenticatorException if encrypted token gets serialized unencrypted" in new Context {
      val s0 = settings.copy(encryptKey = Some("amazing string"))
      val jwt = serialize.copy(settings = s0)(authenticator)
      val s1 = settings.copy(encryptKey = None)
      await(deserialize.copy(settings = s1)(jwt)) must throwA[AuthenticatorException]
    }

    "deserialize a JWT with an encrypted subject" in new Context {
      val s = settings.copy(encryptKey = Some("amazing string"))
      val jwt = serialize.copy(settings = s)(authenticator)
      await(deserialize.copy(settings = s)(jwt)) must beSome(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0),
        idleTimeout = s.authenticatorIdleTimeout
      ))
    }

    "deserialize a JWT without an encrypted subject" in new Context {
      val s = settings.copy(encryptKey = None)
      val jwt = serialize.copy(settings = s)(authenticator)
      await(deserialize(jwt)) must beSome(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0),
        idleTimeout = s.authenticatorIdleTimeout
      ))
    }

    "deserialize a JWT with arbitrary claims" in new Context {
      val s = settings.copy(encryptKey = None)
      val jwt = serialize(authenticator.copy(customClaims = Some(customClaims)))
      await(deserialize(jwt)).flatMap(_.customClaims) must beSome(customClaims)
    }

    "throw an AuthenticatorException if an unexpected value was found in the arbitrary claims" in new Context {
      val claims = Map("null" -> JsNull)
      serialize(authenticator.copy(customClaims = Some(claims))) must throwA[AuthenticatorException]
    }
  }

  trait Context extends Scope with Common {
    val loginInfo = LoginInfo("provider", "this is identificator of user")
    val authenticator = JwtAuthenticator(
      id = "identificator",
      loginInfo = loginInfo,
      lastUsedDateTime = DateTime.now,
      expirationDateTime = DateTime.now,
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
    val deserialize = FromStringJwtAuthenticatorExtractor(settings)

  }

}

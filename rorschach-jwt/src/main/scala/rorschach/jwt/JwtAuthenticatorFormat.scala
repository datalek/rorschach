package rorschach.jwt

import com.atlassian.jwt.SigningAlgorithm
import com.atlassian.jwt.core.writer._
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import play.api.libs.json.Json
import rorschach.core.LoginInfo
import rorschach.exceptions._
import rorschach.util._
import scala.util._
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object JwtAuthenticatorFormat {

  implicit val jsonFormat = Json.format[LoginInfo]

  /**
   * The reserved claims used by the authenticator.
   */
  val ReservedClaims = Seq("jti", "iss", "sub", "iat", "exp", "nbf")

  def serialize(authenticator: JwtAuthenticator)(settings: JwtAuthenticatorSettings): String = {
    val subject = serializeLoginInfo(authenticator.loginInfo).toString
    val jwtBuilder = new JsonSmartJwtJsonBuilder()
      .jwtId(authenticator.id)
      .issuer(settings.issuerClaim)
      .notBefore(authenticator.lastUsedDateTime.getEpochSecond)
      .subject(settings.encryptKey.fold(Base64.encode(subject))(Crypto.encrypt(_, subject)))
      .issuedAt(authenticator.lastUsedDateTime.getEpochSecond)
      .expirationTime(authenticator.expirationDateTime.getEpochSecond)
    val containReserved = authenticator.customClaims.exists(_.exists(s => ReservedClaims.contains(s._1)))
    if (containReserved) throw new AuthenticatorException("Found a reserved key")
    authenticator.customClaims.map(this.serializeCustomClaims).foreach(p => p.map { case (k, v) => jwtBuilder.claim(k, v) })
    new NimbusJwtWriterFactory()
      .macSigningWriter(SigningAlgorithm.HS256, settings.sharedSecret)
      .jsonToJwt(jwtBuilder.build())
  }

  def deserialize(str: String)(settings: JwtAuthenticatorSettings): Try[JwtAuthenticator] = {
    Try {
      val verifier = new MACVerifier(settings.sharedSecret)
      val jwsObject = JWSObject.parse(str)
      if (!jwsObject.verify(verifier)) throw new IllegalArgumentException("Fraudulent JWT token: " + str)
      JWTClaimsSet.parse(jwsObject.getPayload.toJSONObject)
    }.flatMap { c =>
      val subject = settings.encryptKey.fold(Base64.decode(c.getSubject))(Crypto.decrypt(_, c.getSubject))
      unserializeLoginInfo(subject).map { loginInfo =>
        val filteredClaims = c.getAllClaims.toMap.filterNot { case (k, v) => ReservedClaims.contains(k) || v == null }
        val customClaims = this.unserializeCustomClaims(filteredClaims)
        JwtAuthenticator(
          id = c.getJWTID,
          loginInfo = loginInfo,
          lastUsedDateTime = c.getIssueTime.toInstant,
          expirationDateTime = c.getExpirationTime.toInstant,
          idleTimeout = settings.authenticatorIdleTimeout,
          customClaims = if (customClaims.isEmpty) None else Some(customClaims)
        )
      }
    }.recover {
      case e => throw new AuthenticatorException("Invalid token")
    }
  }

  private def serializeCustomClaims(claims: Map[String, Any]): java.util.Map[String, Any] = {
    def toJava(value: Any): Any = value match {
      case v: String => v
      case v: Number => v
      case v: Boolean => v
      case v: Map[_, _] => serializeCustomClaims(v.asInstanceOf[Map[String, Any]])
      case v: Iterable[_] => v.map(toJava).asJava
      case v => throw new AuthenticatorException("Unexpected value")
    }
    claims.map { case (name, value) => name -> toJava(value) }.asJava
  }

  private def unserializeCustomClaims(claims: java.util.Map[String, Any]): Map[String, Any] = {
    def toScala(value: Any): Any = value match {
      case v: java.lang.String => v
      case v: java.lang.Number => BigDecimal(v.toString)
      case v: java.lang.Boolean => Boolean.box(v)
      case v: java.util.Map[_, _] => unserializeCustomClaims(v.asInstanceOf[java.util.Map[String, Any]])
      case v: java.util.List[_] => v.map(toScala).toList
      case v => throw new AuthenticatorException("Unexpected type")
    }
    claims.map { case (name, value) => name -> toScala(value) }.toMap
  }

  def unserializeLoginInfo(str: String): Try[LoginInfo] = {
    Try(Json.parse(str)) match {
      case Success(json) =>
        // We needn't check here if the given Json is a valid LoginInfo object, because the
        // token will be signed and therefore the login info can't be manipulated. So if we
        // serialize an authenticator into a JWT, then this JWT is always the same authenticator
        // after deserialization
        Success(json.as[LoginInfo])
      case Failure(error) =>
        // This error can occur if an authenticator was serialized with the setting encryptSubject=true
        // and deserialized with the setting encryptSubject=false
        Failure(new AuthenticatorException("JsonParseError"))
    }
  }

  def serializeLoginInfo(loginInfo: LoginInfo): String = Json.toJson(loginInfo).toString()

}

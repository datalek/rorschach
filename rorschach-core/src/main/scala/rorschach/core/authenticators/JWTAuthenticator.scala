package rorschach.core.authenticators

import com.atlassian.jwt.SigningAlgorithm
import com.atlassian.jwt.core.writer.{NimbusJwtWriterFactory, JsonSmartJwtJsonBuilder}
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import org.joda.time.DateTime
import rorschach.core.services.AuthenticatorService
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.{StorableAuthenticator, ExpirableAuthenticator, LoginInfo}
import rorschach.exceptions._
import rorschach.util.{Crypto, Clock, IdGenerator, Base64}
import play.api.libs.json._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util._

/**
 * The settings for the JWT authenticator.
 *
 * @param headerName The name of the header in which the token will be transferred.
 * @param issuerClaim The issuer claim identifies the principal that issued the JWT.
 * @param encryptSubject Indicates if the subject should be encrypted in JWT.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 * @param sharedSecret The shared secret to sign the JWT.
 */
case class JWTAuthenticatorSettings(
  headerName: String = "X-Auth-Token",
  issuerClaim: String = "rorschach",
  encryptKey: Option[String] = None,
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12.hours,
  sharedSecret: String)

/**
 * An authenticator that uses a header based approach with the help of a JWT. It works by
 * using a JWT to transport the authenticator data inside a user defined header. It can
 * be stateless with the disadvantages that the JWT can't be invalidated.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property. If this feature is activated then a new token will be generated on every update.
 * Make sure your application can handle this case.
 *
 * @see http://self-issued.info/docs/draft-ietf-oauth-json-web-token.html#Claims
 * @see https://developer.atlassian.com/static/connect/docs/concepts/understanding-jwt.html
 *
 * @param id The authenticator ID.
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 * @param customClaims Custom claims to embed into the token.
 */
case class JWTAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration],
  customClaims: Option[Map[String, Any]] = None)
  extends StorableAuthenticator with ExpirableAuthenticator {
  // serialized to type Value
  override type Value = String
}

object JWTAuthenticator {

  /**
   * The reserved claims used by the authenticator.
   */
  val ReservedClaims = Seq("jti", "iss", "sub", "iat", "exp", "nbf")

  def serialize(authenticator: JWTAuthenticator)(settings: JWTAuthenticatorSettings): String = {
    val subject = serializeLoginInfo(authenticator.loginInfo).toString
    val jwtBuilder = new JsonSmartJwtJsonBuilder()
      .jwtId(authenticator.id)
      .issuer(settings.issuerClaim)
      .notBefore(authenticator.lastUsedDateTime.toDate.getTime / 1000)
      .subject(settings.encryptKey.fold(Base64.encode(subject))(Crypto.encrypt(_, subject)))
      .issuedAt(authenticator.lastUsedDateTime.toDate.getTime / 1000)
      .expirationTime(authenticator.expirationDateTime.toDate.getTime / 1000)
    val containReserved = authenticator.customClaims.exists(_.exists(s => ReservedClaims.contains(s._1)))
    if (containReserved) throw new AuthenticatorException("Found a reserved key")
    authenticator.customClaims.map(this.serializeCustomClaims).foreach(p => p.map{ case (k, v) => jwtBuilder.claim(k, v)})
    new NimbusJwtWriterFactory()
      .macSigningWriter(SigningAlgorithm.HS256, settings.sharedSecret)
      .jsonToJwt(jwtBuilder.build())
  }

  def unserialize(str: String)(settings: JWTAuthenticatorSettings): Try[JWTAuthenticator] = {
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
        JWTAuthenticator(
          id = c.getJWTID,
          loginInfo = loginInfo,
          lastUsedDateTime = new DateTime(c.getIssueTime),
          expirationDateTime = new DateTime(c.getExpirationTime),
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

class JWTAuthenticatorService(
  idGenerator: IdGenerator,
  settings: JWTAuthenticatorSettings,
  dao: Option[AuthenticatorDao[JWTAuthenticator]],
  clock: Clock = Clock
)(implicit val ec: ExecutionContext) extends AuthenticatorService[JWTAuthenticator] {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo): Future[JWTAuthenticator] = {
    idGenerator.generate.map { id =>
      val now = clock.now
      JWTAuthenticator(
        id = id,
        loginInfo = loginInfo,
        lastUsedDateTime = now,
        expirationDateTime = now.plus(settings.authenticatorExpiry.toMillis),
        idleTimeout = settings.authenticatorIdleTimeout
      )
    }.recover {
      case e => throw new AuthenticatorCreationException("", e)
    }
  }

  /**
   * Initializes an authenticator.
   *
   * @param authenticator The authenticator instance.
   * @return The serialized authenticator value.
   */
  override def init(authenticator: JWTAuthenticator): Future[JWTAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.add(authenticator))
  }

  /**
   * Updates a touched authenticator.
   *
   * If the authenticator was updated, then the updated artifacts should be embedded into the response.
   * This method gets called on every subsequent request if an identity accesses a Silhouette action,
   * expect the authenticator was not touched.
   *
   * @param authenticator The authenticator to update.
   * @return The original or a manipulated result.
   */
  override def update(authenticator: JWTAuthenticator): Future[JWTAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.update(authenticator)).recover {
      case e => throw new AuthenticatorUpdateException("Could not update authenticator", e)
    }
  }

  /**
   * Touches an authenticator.
   *
   * An authenticator can use sliding window expiration. This means that the authenticator times
   * out after a certain time if it wasn't used. So to mark an authenticator as used it will be
   * touched on every request to a Silhouette action. If an authenticator should not be touched
   * because of the fact that sliding window expiration is disabled, then it should be returned
   * on the left, otherwise it should be returned on the right. An untouched authenticator needn't
   * be updated later by the [[update]] method.
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  override def touch(authenticator: JWTAuthenticator): scala.Either[JWTAuthenticator, JWTAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) Right(authenticator.copy(lastUsedDateTime = clock.now))
    else Left(authenticator)
  }

  /**
   * Manipulates the response and removes authenticator specific artifacts before sending it to the client.
   *
   * @param authenticator The authenticator instance.
   * @return The manipulated result.
   */
  override def remove(authenticator: JWTAuthenticator): Future[JWTAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.remove(authenticator.id)).recover {
      case e => throw new AuthenticatorDiscardingException("Could not discard authenticator", e)
    }
  }

  /**
   * Renews the expiration of an authenticator without embedding it into the result.
   *
   * Based on the implementation, the renew method should revoke the given authenticator first, before
   * creating a new one. If the authenticator was updated, then the updated artifacts should be returned.
   *
   * @param authenticator The authenticator to renew.
   * @return The renewed authenticator.
   */
  override def renew(authenticator: JWTAuthenticator): Future[JWTAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.remove(authenticator.id)).flatMap { _ =>
      create(authenticator.loginInfo)
    }.recover {
      case e => throw new AuthenticatorRenewalException("Could not reniew authenticator", e)
    }
  }
}
package rorschach.core.authenticators

import org.joda.time.DateTime
import play.api.libs.json.Json
import rorschach.core.services.AuthenticatorService
import rorschach.core.{ExpirableAuthenticator, Authenticator, LoginInfo}
import rorschach.exceptions._
import rorschach.util.{Clock, Base64}
import rorschach.util.JsonFormats._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util._

/**
 * An authenticator that uses a stateless, session based approach. It works by storing a
 * serialized authenticator instance in the Play Framework session cookie.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property.
 *
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 * @param fingerprint Maybe a fingerprint of the user.
 */
case class SessionAuthenticator(
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration],
  fingerprint: Option[String])
  extends Authenticator with ExpirableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = String
}

/**
 * The companion object of the authenticator.
 */
object SessionAuthenticator {

  /**
   * Converts the SessionAuthenticator to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[SessionAuthenticator]

  /**
   * Serializes the authenticator.
   *
   * @param authenticator The authenticator to serialize.
   * @param settings The authenticator settings.
   * @return The serialized authenticator.
   */
  def serialize(authenticator: SessionAuthenticator)(settings: SessionAuthenticatorSettings) = {
    if (settings.encryptAuthenticator) Base64.encode(Json.toJson(authenticator))//Crypto.encryptAES(Json.toJson(authenticator).toString())
    else Base64.encode(Json.toJson(authenticator))
  }

  /**
   * Unserializes the authenticator.
   *
   * @param str The string representation of the authenticator.
   * @param settings The authenticator settings.
   * @return Some authenticator on success, otherwise None.
   */
  def unserialize(str: String)(settings: SessionAuthenticatorSettings): Try[SessionAuthenticator] = {
    if (settings.encryptAuthenticator) buildAuthenticator(Base64.decode(str)/*Crypto.decryptAES(str)*/)
    else buildAuthenticator(Base64.decode(str))
  }

  /**
   * Builds the authenticator from Json.
   *
   * @param str The string representation of the authenticator.
   * @return Some authenticator on success, otherwise None.
   */
  private def buildAuthenticator(str: String): Try[SessionAuthenticator] = {
    Try(Json.parse(str)) match {
      case Success(json) => json.validate[SessionAuthenticator].asEither match {
        case Left(error) => Failure(new AuthenticatorException("Invalid Json format"))
        case Right(authenticator) => Success(authenticator)
      }
      case Failure(error) => Failure(new AuthenticatorException("Cannot parse Json", error))
    }
  }
}

/**
 * The service that handles the session authenticator.
 *
 * @param settings The authenticator settings.
 * //@param fingerprintGenerator The fingerprint generator implementation.
 * @param clock The clock implementation.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class SessionAuthenticatorService(
  settings: SessionAuthenticatorSettings,
  //fingerprintGenerator: FingerprintGenerator,
  clock: Clock)(implicit val executionContext: ExecutionContext)
  extends AuthenticatorService[SessionAuthenticator] {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo): Future[SessionAuthenticator] = {
    Future.successful(Try {
      val now = clock.now
      SessionAuthenticator(
        loginInfo = loginInfo,
        lastUsedDateTime = now,
        expirationDateTime = now.plusMillis(settings.authenticatorExpiry.toMillis.toInt),
        idleTimeout = settings.authenticatorIdleTimeout,
        fingerprint = if (settings.useFingerprinting) /*Some(fingerprintGenerator.generate)*/None else None
      )
    }.recover {
      case e => throw new AuthenticatorCreationException("Error", e)
    }.get)
  }

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  override def touch(authenticator: SessionAuthenticator): Either[SessionAuthenticator, SessionAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) Left(authenticator.copy(lastUsedDateTime = clock.now))
    else Right(authenticator)
  }

  /**
   * Updates the authenticator and store it in the user session.
   *
   * Because of the fact that we store the authenticator client side in the user session, we must update
   * the authenticator in the session on every subsequent request to keep the last used date in sync.
   *
   * @param authenticator The authenticator to update.
   * @return The original or a manipulated result.
   */
  override def update(authenticator: SessionAuthenticator): Future[SessionAuthenticator] = {
    Future.successful(authenticator)
  }

  /**
   * Removes the authenticator from session.
   *
   * @param authenticator The authenticator to update.
   * @return The removed authenticator.
   */
  override def remove(authenticator: SessionAuthenticator): Future[SessionAuthenticator] = {
    Future.successful(authenticator)
  }

  /**
   * Renews the expiration of an authenticator without embedding it into the result.
   *
   * Based on the implementation, the renew method should revoke the given authenticator first, before
   * creating a new one. If the authenticator was updated, then the updated artifacts should be returned.
   *
   * @param authenticator The authenticator to renew.
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: SessionAuthenticator): Future[SessionAuthenticator] = {
    create(authenticator.loginInfo).recover {
      case e => throw new AuthenticatorRenewalException("Error", e)
    }
  }
}

/**
 * The companion object of the authenticator service.
 */
object SessionAuthenticatorService {

  /**
   * The ID of the authenticator.
   */
  val ID = "session-authenticator"

  /**
   * The error messages.
   */
  val JsonParseError = "[Silhouette][%s] Cannot parse Json: %s"
  val InvalidJsonFormat = "[Silhouette][%s] Invalid Json format: %s"
  val InvalidFingerprint = "[Silhouette][%s] Fingerprint %s doesn't match authenticator: %s"
}

/**
 * The settings for the session authenticator.
 *
 * @param sessionKey The key of the authenticator in the session.
 * @param encryptAuthenticator Indicates if the authenticator should be encrypted in session.
 * @param useFingerprinting Indicates if a fingerprint of the user should be stored in the authenticator.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 */
case class SessionAuthenticatorSettings(
  sessionKey: String = "authenticator",
  encryptAuthenticator: Boolean = true,
  useFingerprinting: Boolean = true,
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12.hours)

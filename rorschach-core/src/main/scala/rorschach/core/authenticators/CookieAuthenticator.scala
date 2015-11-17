package rorschach.core.authenticators

import org.joda.time.DateTime
import play.api.libs.json.Json
import rorschach.core._
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.AuthenticatorService
import rorschach.exceptions._
import rorschach.util.{IdGenerator, Clock, Base64}
import rorschach.util.JsonFormats._

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * An authenticator that uses a stateful as well as stateless, cookie based approach.
 *
 * It works either by storing an ID in a cookie to track the authenticated user and a server side backing
 * store that maps the ID to an authenticator instance or by a stateless approach that stores the authenticator
 * in a serialized form directly into the cookie. The stateless approach could also be named “server side session”.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property.
 *
 * With this authenticator it's possible to implement "Remember Me" functionality. This can be
 * achieved by updating the `expirationDateTime`, `idleTimeout` or `cookieMaxAge` properties of
 * this authenticator after it was created and before it gets initialized.
 *
 * Note: If deploying to multiple nodes the backing store will need to synchronize.
 *
 * @param id The authenticator ID.
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 * @param cookieMaxAge The duration a cookie expires. `None` for a transient cookie.
 * @param fingerprint Maybe a fingerprint of the user.
 */

case class CookieAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration],
  cookieMaxAge: Option[FiniteDuration])
  extends StorableAuthenticator with ExpirableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = String
}

/**
 * The companion object of the authenticator.
 */
object CookieAuthenticator {

  /**
   * Converts the CookieAuthenticator to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[CookieAuthenticator]

  /**
   * Serializes the authenticator.
   *
   * @param authenticator The authenticator to serialize.
   * @param settings The authenticator settings.
   * @return The serialized authenticator.
   */
  def serialize(authenticator: CookieAuthenticator)(settings: CookieAuthenticatorSettings) = {
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
  def unserialize(str: String)(settings: CookieAuthenticatorSettings): Try[CookieAuthenticator] = {
    if (settings.encryptAuthenticator) buildAuthenticator(Base64.decode(str)/*Crypto.decryptAES(str)*/)
    else buildAuthenticator(Base64.decode(str))
  }

  /**
   * Builds the authenticator from Json.
   *
   * @param str The string representation of the authenticator.
   * @return Some authenticator on success, otherwise None.
   */
  private def buildAuthenticator(str: String): Try[CookieAuthenticator] = {
    Try(Json.parse(str)) match {
      case Success(json) => json.validate[CookieAuthenticator].asEither match {
        case Left(error) => Failure(new AuthenticatorException("Cannot parse Json"))
        case Right(authenticator) => Success(authenticator)
      }
      case Failure(error) => Failure(new AuthenticatorException("Invalid Json format", error))
    }
  }
}

/**
 * The service that handles the cookie authenticator.
 *
 * @param settings The cookie settings.
 * @param dao The DAO to store the authenticator. Set it to None to use a stateless approach.
 * //@param fingerprintGenerator The fingerprint generator implementation.
 * @param idGenerator The ID generator used to create the authenticator ID.
 * @param clock The clock implementation.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class CookieAuthenticatorService(
  idGenerator: IdGenerator,
  settings: CookieAuthenticatorSettings,
  dao: Option[AuthenticatorDao[CookieAuthenticator]],
  //fingerprintGenerator: FingerprintGenerator,
  clock: Clock = Clock)(implicit val executionContext: ExecutionContext)
  extends AuthenticatorService[CookieAuthenticator] {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo): Future[CookieAuthenticator] = {
      idGenerator.generate.map { id =>
        val now = clock.now
        CookieAuthenticator(
          id = id,
          loginInfo = loginInfo,
          lastUsedDateTime = now,
          expirationDateTime = now.plusMillis(settings.authenticatorExpiry.toMillis.toInt),
          idleTimeout = settings.authenticatorIdleTimeout,
          cookieMaxAge = settings.cookieMaxAge
          //if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
        )
      }.recover {
        case e => throw new AuthenticatorCreationException("Error", e)
      }
  }

  /**
   * Initializes an authenticator.
   *
   * @param authenticator The authenticator instance.
   * @return The serialized authenticator value.
   */
  override def init(authenticator: CookieAuthenticator): Future[CookieAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.add(authenticator))
  }

  /**
   * Updates the authenticator with the new last used date.
   *
   * If the stateless approach will be used then we update the cookie on the client. With the stateful approach
   * we needn't embed the cookie in the response here because the cookie itself will not be changed. Only the
   * authenticator in the backing store will be changed.
   *
   * @param authenticator The authenticator to update.
   * @return The original or a manipulated result.
   */
    override def update(authenticator: CookieAuthenticator): Future[CookieAuthenticator] = {
      dao.fold(Future.successful(authenticator))(_.update(authenticator)).recover {
        case e => throw new AuthenticatorUpdateException("Could not update authenticator", e)
      }
    }

  /**
   * Touches an authenticator.
   *
   * An authenticator can use sliding window expiration. This means that the authenticator times
   * out after a certain time if it wasn't used. So to mark an authenticator as used it will be
   * touched on every request. If an authenticator should not be touched
   * because of the fact that sliding window expiration is disabled, then it should be returned
   * on the left, otherwise it should be returned on the right. An untouched authenticator needn't
   * be updated later by the [[update]] method.
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the right or the untouched authenticator on the left.
   */
  override def touch(authenticator: CookieAuthenticator): Either[CookieAuthenticator, CookieAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) Right(authenticator.copy(lastUsedDateTime = clock.now))
    else Left(authenticator)
  }

  /**
   * Manipulates the response and removes authenticator specific artifacts before sending it to the client.
   *
   * @param authenticator The authenticator instance.
   * @return The manipulated result.
   */
  override def remove(authenticator: CookieAuthenticator): Future[CookieAuthenticator] = {
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
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: CookieAuthenticator): Future[CookieAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.remove(authenticator.id)).flatMap { _ =>
      create(authenticator.loginInfo)
    }.recover {
      case e => throw new AuthenticatorRenewalException("Could not reniew authenticator", e)
    }
  }
}

/**
 * The settings for the cookie authenticator.
 *
 * @param cookieName The cookie name.
 * @param cookiePath The cookie path.
 * @param cookieDomain The cookie domain.
 * @param secureCookie Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param useFingerprinting Indicates if a fingerprint of the user should be stored in the authenticator.
 * @param cookieMaxAge The duration a cookie expires. `None` for a transient cookie.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 */
case class CookieAuthenticatorSettings(
  cookieName: String = "id",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  encryptAuthenticator: Boolean = true,
  useFingerprinting: Boolean = true,
  cookieMaxAge: Option[FiniteDuration] = None,
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12.hours)


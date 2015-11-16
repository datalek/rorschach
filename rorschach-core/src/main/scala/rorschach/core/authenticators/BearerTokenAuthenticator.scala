package rorschach.core.authenticators

import org.joda.time.DateTime
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services._
import rorschach.core.{ExpirableAuthenticator, StorableAuthenticator, LoginInfo}
import rorschach.exceptions.{AuthenticatorRenewalException, AuthenticatorDiscardingException, AuthenticatorUpdateException, AuthenticatorCreationException}
import rorschach.util.{Clock, IdGenerator}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

/**
 * An authenticator that uses a header based approach with the help of a bearer token. It
 * works by transporting a token in a user defined header to track the authenticated user
 * and a server side backing store that maps the token to an authenticator instance.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property.
 *
 * Note: If deploying to multiple nodes the backing store will need to synchronize.
 *
 * @param id The authenticator ID.
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 */
case class BearerTokenAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration])
  extends StorableAuthenticator with ExpirableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = String
}

/**
 * The service that handles the bearer token authenticator.
 */
class BearerTokenAuthenticatorService(
  idGenerator: IdGenerator,
  settings: BearerTokenAuthenticatorSettings,
  dao: AuthenticatorDao[BearerTokenAuthenticator],
  clock: Clock = Clock
  )(implicit val ec: ExecutionContext) extends AuthenticatorService[BearerTokenAuthenticator]{

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo): Future[BearerTokenAuthenticator] = {
    idGenerator.generate.map { id =>
      val now = clock.now
      BearerTokenAuthenticator(
        id = id,
        loginInfo = loginInfo,
        lastUsedDateTime = now,
        expirationDateTime = now.plus(settings.authenticatorExpiry.toMillis),
        idleTimeout = settings.authenticatorIdleTimeout)
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
  override def init(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    dao.add(authenticator)
  }

  /**
   * Updates the authenticator with the new last used date in the backing store.
   *
   * We needn't embed the token in the response here because the token itself will not be changed.
   * Only the authenticator in the backing store will be changed.
   *
   * @param authenticator The authenticator to update.
   * @return The original or a manipulated result.
   */
  override def update(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    dao.update(authenticator).recover {
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
  override def touch(authenticator: BearerTokenAuthenticator): Either[BearerTokenAuthenticator, BearerTokenAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) Right(authenticator.copy(lastUsedDateTime = clock.now))
    else Left(authenticator)
  }

  /**
   * Removes the authenticator from cache.
   *
   * @param authenticator The authenticator instance.
   * @return The manipulated result.
   */
  override def remove(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    dao.remove(authenticator.id).map{ _ =>
      authenticator
    }.recover {
      case e => throw new AuthenticatorDiscardingException("Could not discard authenticator", e)
    }
  }

  /**
   * Renews an authenticator.
   *
   * After that it isn't possible to use a bearer token which was bound to this authenticator. This
   * method doesn't embed the the authenticator into the result. This must be done manually if needed
   * or use the other renew method otherwise.
   *
   * @param authenticator The authenticator to renew.
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    dao.remove(authenticator.id).flatMap { _ =>
      create(authenticator.loginInfo)
    }.recover {
      case e => throw new AuthenticatorRenewalException("Could not reniew authenticator", e)
    }
  }
}

/**
 * The settings for the bearer token authenticator.
 *
 * @param headerName The name of the header in which the token will be transferred.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 */
case class BearerTokenAuthenticatorSettings(
  headerName: String = "X-Auth-Token",
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12 hours)

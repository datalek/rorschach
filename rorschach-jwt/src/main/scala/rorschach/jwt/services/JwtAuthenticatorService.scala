package rorschach.jwt.services

import rorschach.jwt._
import rorschach.util._
import rorschach.exceptions._
import rorschach.core._
import rorschach.core.daos._
import rorschach.core.services._
import scala.concurrent._

class JwtAuthenticatorService(
    idGenerator: IdGenerator,
    settings: JwtAuthenticatorSettings,
    dao: Option[AuthenticatorDao[JwtAuthenticator]],
    clock: Clock = Clock
)(implicit val ec: ExecutionContext) extends AuthenticatorService[JwtAuthenticator] {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo): Future[JwtAuthenticator] = {
    idGenerator.generate.map { id =>
      val now = clock.now
      JwtAuthenticator(
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
  override def init(authenticator: JwtAuthenticator): Future[JwtAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.add(authenticator))
  }

  /**
   * Updates a touched authenticator.
   *
   * If the authenticator was updated, then the updated artifacts should be embedded into the response.
   * This method gets called on every subsequent request if an identity accesses
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator.
   */
  override def update(authenticator: JwtAuthenticator): Future[JwtAuthenticator] = {
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
  override def touch(authenticator: JwtAuthenticator): scala.Either[JwtAuthenticator, JwtAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) Right(authenticator.copy(lastUsedDateTime = clock.now))
    else Left(authenticator)
  }

  /**
   * Manipulates the response and removes authenticator specific artifacts before sending it to the client.
   *
   * @param authenticator The authenticator instance.
   * @return The manipulated result.
   */
  override def remove(authenticator: JwtAuthenticator): Future[JwtAuthenticator] = {
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
  override def renew(authenticator: JwtAuthenticator): Future[JwtAuthenticator] = {
    dao.fold(Future.successful(authenticator))(_.remove(authenticator.id)).flatMap { _ =>
      create(authenticator.loginInfo)
    }.recover {
      case e => throw new AuthenticatorRenewalException("Could not reniew authenticator", e)
    }
  }

}

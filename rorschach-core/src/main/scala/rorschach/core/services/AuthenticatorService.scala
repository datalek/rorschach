package rorschach.core.services

import rorschach.core.{LoginInfo, Authenticator}

import scala.concurrent.Future

/**
 * Handles authenticators
 *
 * @tparam T The type of the authenticator this service is responsible for.
 */
trait AuthenticatorService[T <: Authenticator] {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @return An authenticator.
   */
  def create(loginInfo: LoginInfo): Future[T]

  /**
   * Touches an authenticator.
   *
   * An authenticator can use sliding window expiration. This means that the authenticator times
   * out after a certain time if it wasn't used. So to mark an authenticator as used it will be
   * touched on every request to a Silhouette action. If an authenticator should not be touched
   * because of the fact that sliding window expiration is disabled, then it should be returned
   * on the right, otherwise it should be returned on the left. An untouched authenticator needn't
   * be updated later by the [[update]] method.
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  def touch(authenticator: T): Either[T, T]

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
  def update(authenticator: T): Future[T]

  /**
   * Renews the expiration of an authenticator without embedding it into the result.
   *
   * Based on the implementation, the renew method should revoke the given authenticator first, before
   * creating a new one. If the authenticator was updated, then the updated artifacts should be returned.
   *
   * @param authenticator The authenticator to renew.
   * @return The serialized expression of the authenticator.
   */
  def renew(authenticator: T): Future[T]

  /**
   * Manipulates the response and removes authenticator specific artifacts before sending it to the client.
   *
   * @param authenticator The authenticator instance.
   * @return The manipulated result.
   */
  def remove(authenticator: T): Future[T]
}

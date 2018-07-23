/**
 * Original work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Derivative work: Rorschach (https://github.com/merle-/rorschach)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rorschach.core.services

import rorschach.core.{ LoginInfo, Authenticator }
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
   * Initializes an authenticator.
   *
   * @param authenticator The authenticator instance.
   * @return The serialized authenticator value.
   */
  def init(authenticator: T): Future[T]

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
  def touch(authenticator: T): Either[T, T]

  /**
   * Updates a touched authenticator.
   *
   * This method update an authenticator on the backing store.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator.
   */
  def update(authenticator: T): Future[T]

  /**
   * Renews the expiration of an authenticator.
   *
   * Based on the implementation, the renew method should revoke the given authenticator first, before
   * creating a new one. If the authenticator was updated, then the updated artifacts should be returned.
   *
   * @param authenticator The authenticator to renew.
   * @return The serialized expression of the authenticator.
   */
  def renew(authenticator: T): Future[T]

  /**
   * Removes authenticator specific artifacts before sending it to the client.
   *
   * @param authenticator The authenticator instance.
   * @return The manipulated result.
   */
  def remove(authenticator: T): Future[T]

}

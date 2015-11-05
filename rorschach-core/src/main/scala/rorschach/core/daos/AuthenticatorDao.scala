package rorschach.core.daos

import rorschach.core.StorableAuthenticator

import scala.concurrent.Future

/**
 * The DAO to persist the authenticator.
 *
 * @tparam T The type of the authenticator to store.
 */
trait AuthenticatorDao[T <: StorableAuthenticator] {

  /**
   * Finds the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return The found authenticator or None if no authenticator could be found for the given ID.
   */
  def find(id: String): Future[Option[T]]

  /**
   * Adds a new authenticator.
   *
   * @param authenticator The authenticator to add.
   * @return The added authenticator.
   */
  def add(authenticator: T): Future[T]

  /**
   * Updates an already existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator.
   */
  def update(authenticator: T): Future[T]

  /**
   * Removes the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return The removed authenticator.
   */
  def remove(id: String): Future[T]
}


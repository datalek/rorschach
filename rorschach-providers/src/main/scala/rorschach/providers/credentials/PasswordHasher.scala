package rorschach.providers.credentials

import rorschach.core.AuthInfo

/**
 * The password details.
 *
 * @param hasher The ID of the hasher used to hash this password.
 * @param password The hashed password.
 * @param salt The optional salt used when hashing.
 */
case class PasswordInfo(hasher: String, password: String, salt: Option[String] = None) extends AuthInfo

/**
 * A trait that defines the password hasher interface.
 */
trait PasswordHasher {

  /**
   * Gets the ID of the hasher.
   *
   * @return The ID of the hasher.
   */
  def id: String

  /**
   * Hashes a password.
   *
   * @param plainPassword The password to hash.
   * @return A PasswordInfo containing the hashed password and optional salt.
   */
  def hash(plainPassword: String): PasswordInfo

  /**
   * Checks whether a supplied password matches the hashed one.
   *
   * @param passwordInfo The password retrieved from the backing store.
   * @param suppliedPassword The password supplied by the user trying to log in.
   * @return True if the password matches, false otherwise.
   */
  def matches(passwordInfo: PasswordInfo, suppliedPassword: String): Boolean
}
package rorschach.core

import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration

/**
 * An authenticator tracks an authenticated user.
 */
trait Authenticator {

  /**
   * Gets the linked login info for an identity.
   *
   * @return The linked login info for an identity.
   */
  def loginInfo: LoginInfo

  /**
   * Checks if the authenticator valid.
   *
   * @return True if the authenticator valid, false otherwise.
   */
  def isValid: Boolean
}

/**
 * An authenticator which can be stored in a backing store.
 */
trait StorableAuthenticator extends Authenticator {

  /**
   * Gets the ID to reference the authenticator in the backing store.
   *
   * @return The ID to reference the authenticator in the backing store.
   */
  def id: String
}

/**
 * An authenticator that may expire.
 */
trait ExpirableAuthenticator extends Authenticator {

  /**
   * The last used date/time.
   */
  val lastUsedDateTime: DateTime

  /**
   * The expiration date/time.
   */
  val expirationDateTime: DateTime

  /**
   * The duration an authenticator can be idle before it timed out.
   */
  val idleTimeout: Option[FiniteDuration]

  /**
   * Checks if the authenticator isn't expired and isn't timed out.
   *
   * @return True if the authenticator isn't expired and isn't timed out.
   */
  override def isValid: Boolean = !isExpired && !isTimedOut

  /**
   * Checks if the authenticator is expired. This is an absolute timeout since the creation of
   * the authenticator.
   *
   * @return True if the authenticator is expired, false otherwise.
   */
  def isExpired: Boolean = expirationDateTime.isBeforeNow

  /**
   * Checks if the time elapsed since the last time the authenticator was used, is longer than
   * the maximum idle timeout specified in the properties.
   *
   * @return True if sliding window expiration is activated and the authenticator is timed out, false otherwise.
   */
  def isTimedOut: Boolean = idleTimeout.isDefined && lastUsedDateTime.plusSeconds(idleTimeout.get.toSeconds.toInt).isBeforeNow
}

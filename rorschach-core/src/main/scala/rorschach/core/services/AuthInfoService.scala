package rorschach.core.services

import rorschach.core.{LoginInfo, AuthInfo}

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * A trait that provides the means to persist authentication information.
 *
 * If the application supports the concept of "merged identities", i.e., the same user being
 * able to authenticate through different providers, then make sure that the auth info for
 * every linked login info gets stored separately.
 */
trait AuthInfoService {

  /**
   * Finds the auth info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @param tag The class tag of the auth info.
   * @tparam T The type of the auth info to handle.
   * @return The found auth info or None if no auth info could be found for the given login info.
   */
  def find[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]]

  /**
   * Adds new auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @tparam T The type of the auth info to handle.
   * @return The saved auth info.
   */
  def add[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T]

  /**
   * Updates the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be updated.
   * @param authInfo The auth info to update.
   * @tparam T The type of the auth info to handle.
   * @return The updated auth info.
   */
  def update[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T]

  /**
   * Saves the auth info for the given login info.
   *
   * This method either adds the auth info if it doesn't exists or it updates the auth info
   * if it already exists.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @tparam T The type of the auth info to handle.
   * @return The updated auth info.
   */
  def save[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T]

  /**
   * Removes the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be removed.
   * @param tag The class tag of the auth info.
   * @tparam T The type of the auth info to handle.
   * @return A future to wait for the process to be completed.
   */
  def remove[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Unit]
}

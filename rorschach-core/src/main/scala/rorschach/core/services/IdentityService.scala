package rorschach.core.services

import rorschach.core.{LoginInfo, Identity}
import scala.concurrent.Future


/**
 * A trait that provides the means to retrieve identities
 */
trait IdentityService[T <: Identity] {

  /**
   * Retrieves an identity that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve an identity.
   * @return The retrieved identity or None if no identity could be retrieved for the given login info.
   */
  def retrieve(loginInfo: LoginInfo): Future[Option[T]]
}
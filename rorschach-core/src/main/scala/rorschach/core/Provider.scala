package rorschach.core

import scala.concurrent.Future

/**
 * A marker interface for all providers.
 */
trait Provider[R, O] {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id: String // TODO: remove this

  def authenticate(in: R): Future[O]
}
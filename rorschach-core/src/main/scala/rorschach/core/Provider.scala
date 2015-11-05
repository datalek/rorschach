package rorschach.core

/**
 * A marker interface for all providers.
 */
trait Provider {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id: String
}
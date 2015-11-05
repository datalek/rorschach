package rorschach.core

/**
 * Credentials to authenticate with.
 *
 * @param identifier The unique identifier to authenticate with.
 * @param password The password to authenticate with.
 */
case class Credentials(identifier: String, password: String)
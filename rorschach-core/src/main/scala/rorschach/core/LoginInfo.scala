package rorschach.core

/**
 * Represents a linked login for an identity (i.e. a local username/password or a Facebook/Google account).
 *
 * The login info contains the data about the provider that authenticated that identity.
 *
 * @param providerId The Id of the provider.
 * @param providerKey A unique key which identifies a user on this provider (userID, email, ...).
 */
case class LoginInfo(providerId: String, providerKey: String)
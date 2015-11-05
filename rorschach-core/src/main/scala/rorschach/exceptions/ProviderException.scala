package rorschach.exceptions

/**
 * Indicates an error occurred with an authentication provider.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class ProviderException(msg: String, cause: Throwable = null)
  extends Exception(msg, cause)

/**
 * Indicates that an invalid password was entered in a credential based provider.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class InvalidPasswordException(msg: String, cause: Throwable = null)
  extends ProviderException(msg, cause)

/**
 * Signals that an identity could not found in a credential based provider.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class IdentityNotFoundException(msg: String, cause: Throwable = null)
  extends ProviderException(msg, cause)

/**
 * Signals that a social provider denies access during authentication process.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AccessDeniedException(msg: String, cause: Throwable = null)
  extends ProviderException(msg, cause)
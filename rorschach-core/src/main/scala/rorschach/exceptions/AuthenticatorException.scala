package rorschach.exceptions

/**
 * An exception for all authenticator related errors.
 */
class AuthenticatorException(msg: String, cause: Throwable = null)
  extends Exception(msg, cause)

/**
 * An exception thrown when there is an error during creation of authenticator.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AuthenticatorCreationException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)

/**
 * An exception thrown when there is an error during authenticator discarding.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AuthenticatorDiscardingException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)

/**
 * An exception thrown when there is an error during authenticator initialization.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AuthenticatorInitializationException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)

/**
 * An exception thrown when there is an error during authenticator update.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AuthenticatorUpdateException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)

/**
 * An exception thrown when there is an error during authenticator update.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AuthenticatorRenewalException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)

/**
 * An exception thrown when there is an error during authenticator update.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class InvalidAuthenticatorException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)

/**
 * An exception thrown when there is an error during authenticator update.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class AuthenticatorNotFoundException(msg: String, cause: Throwable = null)
  extends AuthenticatorException(msg, cause)
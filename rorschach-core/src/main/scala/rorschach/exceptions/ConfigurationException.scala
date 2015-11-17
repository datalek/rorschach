package rorschach.exceptions

/**
 * Indicates a misconfiguration problem.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class ConfigurationException(msg: String, cause: Throwable = null)
  extends Exception(msg, cause)

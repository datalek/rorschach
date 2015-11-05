package rorschach.exceptions

/**
 * Indicates a misconfiguration of a Silhouette component.
 *
 * @param msg The exception message.
 * @param cause The exception cause.
 */
class ConfigurationException(msg: String, cause: Throwable = null)
  extends Exception(msg, cause)

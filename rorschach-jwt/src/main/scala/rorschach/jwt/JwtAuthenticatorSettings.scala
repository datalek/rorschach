package rorschach.jwt

import scala.concurrent.duration._

/**
 * The settings for the JWT authenticator.
 *
 * @param issuerClaim The issuer claim identifies the principal that issued the JWT.
 * @param encryptKey The key to use to encrypt the subject, if none no encryption will done.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 * @param sharedSecret The shared secret to sign the JWT.
 */
case class JwtAuthenticatorSettings(
  issuerClaim: String = "rorschach",
  encryptKey: Option[String] = None,
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12.hours,
  sharedSecret: String
)

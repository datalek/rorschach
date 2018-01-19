package rorschach.jwt

import java.time.Instant
import rorschach.core._
import scala.concurrent.duration.FiniteDuration

/**
 * An authenticator that uses a header based approach with the help of a JWT. It works by
 * using a JWT to transport the authenticator data inside a user defined header. It can
 * be stateless with the disadvantages that the JWT can't be invalidated.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property. If this feature is activated then a new token will be generated on every update.
 * Make sure your application can handle this case.
 *
 * @see http://self-issued.info/docs/draft-ietf-oauth-json-web-token.html#Claims
 * @see https://developer.atlassian.com/static/connect/docs/concepts/understanding-jwt.html
 * @param id The authenticator ID.
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 * @param customClaims Custom claims to embed into the token.
 */
case class JwtAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDateTime: Instant,
  expirationDateTime: Instant,
  idleTimeout: Option[FiniteDuration],
  customClaims: Option[Map[String, Any]] = None
) extends StorableAuthenticator with ExpirableAuthenticator

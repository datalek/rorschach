package rorschach.core

import play.api.libs.json.Json

/**
 * Represents a linked login for an identity (i.e. a local username/password or a Facebook/Google account).
 *
 * The login info contains the data about the provider that authenticated that identity.
 *
 * @param providerId The Id of the provider.
 * @param providerKey A unique key which identifies a user on this provider (userID, email, ...).
 */
case class LoginInfo(providerId: String, providerKey: String)

/**
 * The companion object of the login info.
 */
object LoginInfo extends ((String, String) => LoginInfo) {

  /**
   * Converts the [[LoginInfo]] to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[LoginInfo]
}
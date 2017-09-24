package rorschach.jwt.embedders

import rorschach.jwt._
import rorschach.core.embedders._

case class ToStringJwtAuthenticatorEmbedder(
    settings: JwtAuthenticatorSettings
) extends AuthenticatorEmbedder[JwtAuthenticator, String] {
  override def apply(v1: JwtAuthenticator): String =
    JwtAuthenticatorFormat.serialize(v1)(settings)
}

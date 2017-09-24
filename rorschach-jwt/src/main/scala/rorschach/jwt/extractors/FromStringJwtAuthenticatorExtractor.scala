package rorschach.jwt.extractors

import rorschach.jwt._
import rorschach.core.extractors._
import rorschach.exceptions._
import scala.concurrent.Future

case class FromStringJwtAuthenticatorExtractor(
    settings: JwtAuthenticatorSettings
) extends AuthenticatorExtractor[String, JwtAuthenticator] {
  override def apply(v1: String): Future[Option[JwtAuthenticator]] =
    JwtAuthenticatorFormat.deserialize(v1)(settings)
      .map(e => Future.successful(Some(e)))
      .recover { case t: AuthenticatorException => Future.failed(t) }
      .getOrElse(Future.successful(None))
}
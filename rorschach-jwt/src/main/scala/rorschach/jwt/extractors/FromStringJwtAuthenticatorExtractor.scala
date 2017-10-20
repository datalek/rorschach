package rorschach.jwt.extractors

import rorschach.jwt._
import rorschach.core.extractors._
import scala.concurrent.Future
import scala.util._

case class FromStringJwtAuthenticatorExtractor(
    settings: JwtAuthenticatorSettings
) extends AuthenticatorExtractor[String, JwtAuthenticator] {
  override def apply(v1: String): Future[Option[JwtAuthenticator]] =
    JwtAuthenticatorFormat.deserialize(v1)(settings) match {
      case Success(jwt) => Future.successful(Some(jwt))
      case Failure(t) => Future.failed(t)
    }
}
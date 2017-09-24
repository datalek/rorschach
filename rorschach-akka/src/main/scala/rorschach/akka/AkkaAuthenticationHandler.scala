package rorschach.akka

import akka.http.scaladsl.server._
import rorschach.core._
import rorschach.core.embedders._
import rorschach.core.extractors._
import rorschach.core.services._
import scala.concurrent.ExecutionContext

case class AkkaAuthenticationHandler[A <: Authenticator, I](
  authenticatorExtractor: AuthenticatorExtractor[RequestContext, A],
  authenticatorService: AuthenticatorService[A],
  identityExtractor: IdentityRetriever[LoginInfo, I],
  authenticatorEmbedder: AuthenticatorEmbedder[A, Directive0]
)(implicit val ec: ExecutionContext) extends AuthenticationHandler[RequestContext, A, I, Directive0]
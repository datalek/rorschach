package rorschach.akka

import akka.http.scaladsl.server._
import rorschach.core._
import rorschach.core.embedders._
import rorschach.core.extractors._
import rorschach.core.services._
import scala.concurrent.ExecutionContext

trait AkkaAuthenticationHandler[I]
  extends AuthenticationHandler[RequestContext, I, Directive0]

object AkkaAuthenticationHandler {
  def apply[A <: Authenticator, I](
    authenticatorExtractor: AuthenticatorExtractor[RequestContext, A],
    authenticatorService: AuthenticatorService[A],
    identityExtractor: IdentityRetriever[LoginInfo, I],
    authenticatorEmbedder: AuthenticatorEmbedder[A, Directive0]
  )(implicit ec: ExecutionContext): AkkaAuthenticationHandler[I] =
    AkkaAuthenticationHandlerImpl(authenticatorExtractor, authenticatorService, identityExtractor, authenticatorEmbedder)(ec)

  case class AkkaAuthenticationHandlerImpl[A0 <: Authenticator, I](
      authenticatorExtractor: AuthenticatorExtractor[RequestContext, A0],
      authenticatorService: AuthenticatorService[A0],
      identityExtractor: IdentityRetriever[LoginInfo, I],
      authenticatorEmbedder: AuthenticatorEmbedder[A0, Directive0]
  )(implicit val ec: ExecutionContext) extends AkkaAuthenticationHandler[I] {
    type A = A0
  }
}
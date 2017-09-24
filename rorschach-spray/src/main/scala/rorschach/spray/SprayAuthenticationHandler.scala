package rorschach.spray

import rorschach.core._
import rorschach.core.embedders._
import rorschach.core.extractors._
import rorschach.core.services._
import scala.concurrent.ExecutionContext
import spray.routing._

trait SprayAuthenticationHandler {

}

object SprayAuthenticationHandler {

  type Alias[A <: Authenticator, I] = AuthenticationHandler[RequestContext, A, I, Directive0]

  def apply[A <: Authenticator, I](
    authenticatorExtractor: AuthenticatorExtractor[RequestContext, A],
    authenticatorService: AuthenticatorService[A],
    identityExtractor: IdentityRetriever[LoginInfo, I],
    authenticatorEmbedder: AuthenticatorEmbedder[A, Directive0]
  )(implicit ec: ExecutionContext) = {
    AuthenticationHandler(
      authenticatorExtractor,
      authenticatorService,
      identityExtractor,
      authenticatorEmbedder
    )
  }

  import rorschach.exceptions.{ AuthenticatorNotFoundException, IdentityNotFoundException, InvalidAuthenticatorException }
  import spray.routing.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
  import spray.routing.authentication.Authentication
  import spray.routing.directives.AuthMagnet
  import spray.routing.directives.BasicDirectives.{ extract, provide }
  import spray.routing.directives.FutureDirectives.onSuccess

  def fromRorschachAuthenticator[A <: Authenticator, T](authenticator: SprayAuthenticationHandler.Alias[A, T])(implicit executor: ExecutionContext): AuthMagnet[T] = {
    val directive: Directive1[Authentication[T]] = extract(authenticator.authenticate).flatMap(onSuccess(_)).flatMap {
      case Right((auth, user, d0)) ⇒ provide[Authentication[T]](Right(user)) & d0
      case Left(t) ⇒ t match {
        case e: IdentityNotFoundException => provide[Authentication[T]](Left(AuthenticationFailedRejection(CredentialsRejected, List())))
        case e: InvalidAuthenticatorException => provide[Authentication[T]](Left(AuthenticationFailedRejection(CredentialsRejected, List())))
        case e: AuthenticatorNotFoundException => provide[Authentication[T]](Left(AuthenticationFailedRejection(CredentialsMissing, List())))
      }
    }
    new AuthMagnet[T](directive)
  }
}

package rorschach.core

import rorschach.exceptions._
import rorschach.core.extractors._
import rorschach.core.embedders._
import rorschach.core.services._
import scala.concurrent._

trait AuthenticationHandler[R, I, O] {
  type A <: Authenticator
  def authenticatorExtractor: AuthenticatorExtractor[R, A]
  def authenticatorService: AuthenticatorService[A]
  def identityExtractor: IdentityRetriever[LoginInfo, I]
  def authenticatorEmbedder: AuthenticatorEmbedder[A, O]
  implicit val ec: ExecutionContext

  def authenticate(in: R): Future[Either[Throwable, (A, I, O)]] = {
    // extract authenticator
    authenticatorExtractor(in).flatMap {
      // a valid authenticator was found so we retrieve also the identity
      case Some(a) if a.isValid => identityExtractor(a.loginInfo).flatMap {
        // the identity is found so return it after update the authenticator (left is not touched, right is touched)
        case Some(i) => authenticatorService.touch(a) match {
          case Left(touched) => Future.successful(Right(touched, i, authenticatorEmbedder(touched)))
          case Right(touched) => authenticatorService.update(touched).map(Right(_, i, authenticatorEmbedder(touched)))
        }
        // the identity wasn't found so discard authenticator and fail with rejection
        case None =>
          authenticatorService.remove(a)
            .map(_ => Left(new IdentityNotFoundException("Identity not found")))
      }
      // an invalid authenticator was found so we needn't retrieve the identity, discard authenticator
      case Some(a) =>
        authenticatorService.remove(a)
          .map(a => Left(new InvalidAuthenticatorException("Invalid authenticator")))
      // no authenticator was found so no identity
      case None =>
        Future.successful(Left(new AuthenticatorNotFoundException("no authenticator found")))

    }
  }

}

object AuthenticationHandler {

  type Aux[R, A0 <: Authenticator, I, O] = AuthenticationHandler[R, I, O] { type A = A0 }

  def apply[R, A0 <: Authenticator, I, O](
    authenticatorExtractor: AuthenticatorExtractor[R, A0],
    authenticatorService: AuthenticatorService[A0],
    identityExtractor: IdentityRetriever[LoginInfo, I],
    authenticatorEmbedder: AuthenticatorEmbedder[A0, O]
  )(implicit ec: ExecutionContext): AuthenticationHandler.Aux[R, A0, I, O] = {
    val (aex, as, ie, aem, _ec) = (authenticatorExtractor, authenticatorService, identityExtractor, authenticatorEmbedder, ec)
    new AuthenticationHandler[R, I, O] {
      type A = A0
      override val authenticatorExtractor = aex
      override val authenticatorService = as
      override val identityExtractor = ie
      override val authenticatorEmbedder = aem
      override implicit val ec = _ec
    }
  }
}
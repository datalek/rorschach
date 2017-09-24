package rorschach.core

import rorschach.exceptions._
import rorschach.core.extractors._
import rorschach.core.embedders._
import rorschach.core.services._
import scala.concurrent._

trait AuthenticationHandler[R, A <: Authenticator, I, O] {

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

  def apply[R, A <: Authenticator, I, O](
    authenticatorExtractor: AuthenticatorExtractor[R, A],
    authenticatorService: AuthenticatorService[A],
    identityExtractor: IdentityRetriever[LoginInfo, I],
    authenticatorEmbedder: AuthenticatorEmbedder[A, O]
  )(implicit ec: ExecutionContext): AuthenticationHandler[R, A, I, O] = {
    val (aex, as, ie, aem, _ec) = (authenticatorExtractor, authenticatorService, identityExtractor, authenticatorEmbedder, ec)
    new AuthenticationHandler[R, A, I, O] {
      override val authenticatorExtractor = aex
      override val authenticatorService = as
      override val identityExtractor = ie
      override val authenticatorEmbedder = aem
      override implicit val ec = _ec

    }
  }

}

//case class AuthenticationHandler[R, A <: Authenticator, I, O](
//    authenticatorExtractor: AuthenticatorExtractor[R, A],
//    authenticatorService: AuthenticatorService[A],
//    identityExtractor: IdentityRetriever[LoginInfo, I],
//    authenticatorEmbedder: AuthenticatorEmbedder[A, O]
//)(implicit ec: ExecutionContext) {
//
//  def authenticate(in: R): Future[Either[Throwable, (A, I, O)]] = {
//    // extract authenticator
//    authenticatorExtractor(in).flatMap {
//      // a valid authenticator was found so we retrieve also the identity
//      case Some(a) if a.isValid => identityExtractor(a.loginInfo).flatMap {
//        // the identity is found so return it after update the authenticator (left is not touched, right is touched)
//        case Some(i) => authenticatorService.touch(a) match {
//          case Left(touched) => Future.successful(Right(touched, i, authenticatorEmbedder(touched)))
//          case Right(touched) => authenticatorService.update(touched).map(Right(_, i, authenticatorEmbedder(touched)))
//        }
//        // the identity wasn't found so discard authenticator and fail with rejection
//        case None =>
//          authenticatorService.remove(a)
//            .map(_ => Left(new IdentityNotFoundException("Identity not found")))
//      }
//      // an invalid authenticator was found so we needn't retrieve the identity, discard authenticator
//      case Some(a) =>
//        authenticatorService.remove(a)
//          .map(a => Left(new InvalidAuthenticatorException("Invalid authenticator")))
//      // no authenticator was found so no identity
//      case None =>
//        Future.successful(Left(new AuthenticatorNotFoundException("no authenticator found")))
//
//    }
//  }
//}

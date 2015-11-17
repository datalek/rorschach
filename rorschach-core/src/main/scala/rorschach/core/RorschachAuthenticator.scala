package rorschach.core

import rorschach.core.services.{IdentityService, AuthenticatorService}
import rorschach.exceptions.{InvalidAuthenticatorException, AuthenticatorNotFoundException, IdentityNotFoundException}
import scala.concurrent.{Future, ExecutionContext}

trait RorschachAuthenticator[A <: Authenticator, I <: Identity] {
  // The context request
  type Context
  // where execute concurrent work
  implicit val ec: ExecutionContext
  protected def identityService: IdentityService[I]
  protected def authenticationService: AuthenticatorService[A]

  def retrieve(ctx: Context): Future[Option[A]]

  /**
   *
   * @param ctx
   * @return
   */
  protected[core] def authenticate(ctx: Context): Future[Either[Throwable, (A, I)]] = {
    this.retrieve(ctx).flatMap {
      // A valid authenticator was found so we retrieve also the identity
      case Some(a) if a.isValid => identityService.retrieve(a.loginInfo).flatMap {
        // the identity is found so return it after update the authenticator (left is not touched, right is touched)
        case Some(i) => authenticationService.touch(a) match {
          case Left(touched) => Future.successful(Right(touched, i))
          case Right(touched) => authenticationService.update(touched).map(Right(_, i))
        }
        // the identity wasn't found so discard authenticator and fail with rejection
        case None => authenticationService.remove(a).map(a => Left(new IdentityNotFoundException("Identity not found")))
      }
      // An invalid authenticator was found so we needn't retrieve the identity, discard authenticator
      case Some(a) => authenticationService.remove(a).map(a => Left(new InvalidAuthenticatorException("Invalid authenticator")))
      // No authenticator was found so no identity
      case None => Future.successful{ Left(new AuthenticatorNotFoundException("no authenticator found")) }
    }
  }
}
package rorschach.spray

import spray.routing._
import spray.routing.directives.AuthMagnet
import spray.routing.directives.BasicDirectives.{extract, provide, pass}
import spray.routing.directives.FutureDirectives.onSuccess
import spray.routing.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import spray.routing.{AuthenticationFailedRejection, RequestContext}
import spray.routing.authentication._
import rorschach.core.services.{IdentityService, AuthenticatorService}
import rorschach.core.{RorschachAuthenticator, LoginInfo, Identity, Authenticator}
import rorschach.exceptions.{IdentityNotFoundException, AuthenticatorNotFoundException, InvalidAuthenticatorException}
import scala.concurrent.{Future, ExecutionContext}


/**
 * Spray types used by Authenticators (defined in spray.routing.authentication):
 *
 * type Authentication[T] = Either[Rejection, T]
 * type ContextAuthenticator[T] = RequestContext ⇒ Future[Authentication[T]]
 */
trait SprayRorschachAuthenticator[A <: Authenticator, I <: Identity]
  extends RorschachAuthenticator[A, I] with ContextAuthenticator[(A, I)] {
  type Context = RequestContext

  def create(loginInfo: LoginInfo): Future[A] = authenticationService.create(loginInfo).flatMap(authenticationService.init)
  def remove(authenticator: A): Future[A] = authenticationService.remove(authenticator)

  def serialize(authenticator: A): A#Value
  def embed(value: A#Value): Directive0
  def unembed: Directive0 = pass

  /**
   * @param ctx the context where retrieve information for authentication
   * @return an authentication
   */
  def apply(ctx: RequestContext): Future[Authentication[(A, I)]] = {
    this.authenticate(ctx).map {
      case Right((authenticator, identity)) => Right(authenticator, identity)
      case Left(e) => e match {
        case e: IdentityNotFoundException => Left(AuthenticationFailedRejection(CredentialsRejected, List()))
        case e: InvalidAuthenticatorException => Left(AuthenticationFailedRejection(CredentialsRejected, List()))
        case e: AuthenticatorNotFoundException => Left(AuthenticationFailedRejection(CredentialsMissing, List()))
        //case other => throw other
      }
    }
  }
}

/**
 * This allow to add a response header or cookie
 */
object SprayRorschachAuthenticator {
  /**
   * This allow to add a response header or cookie
   * @param authenticator
   * @param executor
   * @tparam A
   * @tparam T
   */
  implicit def fromRorschachAuthenticator[A <: Authenticator, T <: Identity](authenticator: SprayRorschachAuthenticator[A, T])(implicit executor: ExecutionContext): AuthMagnet[T] = {
    val directive: Directive1[Authentication[T]] = extract(authenticator).flatMap(onSuccess(_)).flatMap {
      case Right((auth, user)) ⇒ provide[Authentication[T]](Right(user)) & authenticator.embed(authenticator.serialize(auth))
      case Left(rejection) ⇒ provide[Authentication[T]](Left(rejection))
    }
    new AuthMagnet[T](directive)
  }
}
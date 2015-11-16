package rorschach.akka

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import rorschach.core.{Authenticator, LoginInfo, RorschachAuthenticator, Identity}
import rorschach.exceptions._
import scala.concurrent.Future
import AkkaRorschachAuthenticator._

trait AkkaRorschachAuthenticator[A <: Authenticator, I <: Identity]
  extends RorschachAuthenticator[A, I] {
  type Context = RequestContext

  def create(loginInfo: LoginInfo): Future[A] = authenticationService.create(loginInfo).flatMap(authenticationService.init)
  def remove(authenticator: A): Future[A] = authenticationService.remove(authenticator)

  val Challenge: HttpChallenge
  def serialize(authenticator: A): A#Value
  def embed(value: A#Value): Directive0
  def unembed: Directive0 = pass
  Credentials
  /**
   * @param ctx the context where retrieve information for authentication
   * @return an authentication
   */
  def apply(ctx: RequestContext): Future[Authentication[(A, I)]] = {
    this.authenticate(ctx).map {
      case Right((authenticator, identity)) => Right(authenticator, identity)
      case Left(e) => e match {
        case e: IdentityNotFoundException => Left(AuthenticationFailedRejection(CredentialsRejected, Challenge))
        case e: InvalidAuthenticatorException => Left(AuthenticationFailedRejection(CredentialsRejected, Challenge))
        case e: AuthenticatorNotFoundException => Left(AuthenticationFailedRejection(CredentialsMissing, Challenge))
        //case other => throw other
      }
    }
  }
}

object AkkaRorschachAuthenticator {
  type Authentication[T] = Either[Rejection, T]
}

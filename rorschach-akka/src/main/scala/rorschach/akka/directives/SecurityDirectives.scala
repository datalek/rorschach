package rorschach.akka.directives

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import rorschach.akka._
import rorschach.core.{ Authenticator, LoginInfo }
import rorschach.exceptions._

trait SecurityDirectives {

  protected def containsCredentialsMissing(in: Seq[Rejection]): Boolean = in
    .collect { case r: AuthenticationFailedRejection => r }
    .exists(_.cause == CredentialsMissing)

  /**
   * Provide an authenticator from a loginInfo
   */
  //  def create[T](authentication: AkkaAuthenticationHandler[T], loginInfo: LoginInfo): Directive1[authentication.A] =
  //    ???

  /**
   * Given an authenticator embed the serialized value and provide this value on inner route
   * Examples: if authenticator is a token it will embed and provided as string (serialized),
   * if is a cookie it wil be embed and provide as [[akka.http.scaladsl.model.headers.Cookie]]...
   */
  //  def embed[A <: Authenticator, O, T](authentication: AkkaAuthenticationHandler.Aux[T, A, O], authenticator: A): O =
  //    authentication.authenticatorEmbedder(authenticator)

  /**
   * Discard authenticator if there is one, otherwise do nothing.
   * Examples: if authenticator is a token it will be remove, if is a cookie it wil be discard, etc...
   */
  //  def discard[A <: Authenticator, T](authenticator: AkkaAuthenticationHandler[T]): Directive0 =
  //    ???

  /**
   * Wraps its inner Route with authentication support.
   * Can be called either with a ``Future[Authentication[T]]`` or ``ContextAuthenticator[T]``.
   */
  def authenticate[T](authenticator: AkkaAuthenticationHandler[T]): Directive1[T] = {
    extract(ctx => authenticator.authenticate(ctx)).flatMap(onSuccess(_)).flatMap {
      case Right((auth, user, d0)) ⇒ provide(user) & d0
      case Left(t) ⇒ t match {
        case e: IdentityNotFoundException => reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenge(scheme = "", realm = "")))
        case e: InvalidAuthenticatorException => reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenge(scheme = "", realm = "")))
        case e: AuthenticatorNotFoundException => reject(AuthenticationFailedRejection(CredentialsMissing, HttpChallenge(scheme = "", realm = "")))
      }
    }
  }

  /**
   * Wraps its inner Route with optional authentication support.
   * Can be called either with a ``Future[Authentication[T]]`` or ``ContextAuthenticator[T]``.
   */
  def optionalAuthenticate[T](authenticator: AkkaAuthenticationHandler[T]): Directive1[Option[T]] = {
    authenticate(authenticator).map(Option(_)).recoverPF {
      case rejections if containsCredentialsMissing(rejections) => provide(None)
    }
  }

  def authenticate[T](authenticators: AkkaAuthenticationHandlerChain[T]): Directive1[T] =
    authenticators match {
      case Nil => failWith(new IllegalStateException("authenticators can't be empty"))
      case h :: t => t.foldLeft(authenticate(h)) {
        case (directive, auth) => directive | authenticate(auth)
      }
    }

  def optionalAuthenticate[T](authenticators: AkkaAuthenticationHandlerChain[T]): Directive1[Option[T]] = {
    authenticate(authenticators).map(Option(_)).recoverPF {
      case rejections if containsCredentialsMissing(rejections) => provide(None)
    }
  }

}

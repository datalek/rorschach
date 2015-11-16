package rorschach.akka.directives

import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import rorschach.akka.AkkaRorschachAuthenticator.Authentication
import rorschach.core.{LoginInfo, Identity, Authenticator}
import rorschach.akka.AkkaRorschachAuthenticator
import scala.concurrent.ExecutionContext

trait SecurityDirectives {
  /**
   * Provide an authenticator from a loginInfo
   */
  def create[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T], loginInfo: LoginInfo): Directive1[A] = magnet.create(loginInfo)

  /**
   * Given an authenticator embed the serialized value and provide this value on inner route
   * Examples: if authenticator is a token it will embed and provided as string (serialized),
   * if is a cookie it wil be embed and provide as [[akka.http.scaladsl.model.headers.Cookie]]...
   */
  def embed[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T], authenticator: A): Directive1[A#Value] = magnet.embed(authenticator)

  /**
   * Discard authenticator if there is one, otherwise do nothing.
   * Examples: if authenticator is a token it will be remove, if is a cookie it wil be discard, etc...
   */
  def discard[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T]): Directive0 = magnet.discard

  /**
   * Wraps its inner Route with authentication support.
   * Can be called either with a ``Future[Authentication[T]]`` or ``ContextAuthenticator[T]``.
   */
  def authenticate[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T]): Directive1[T] = magnet.authenticate

  /**
   * Wraps its inner Route with optional authentication support.
   * Can be called either with a ``Future[Authentication[T]]`` or ``ContextAuthenticator[T]``.
   */
  def optionalAuthenticate[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T]): Directive1[Option[T]] = magnet.optionalAuthenticate

}

class RorschachAuthMagnet[A <: Authenticator, T <: Identity](authenticator: AkkaRorschachAuthenticator[A, T])(implicit executor: ExecutionContext) {

  val create: (LoginInfo) => Directive1[A] = (loginInfo) => onSuccess(authenticator.create(loginInfo)).flatMap(a => provide(a))

  val embed: (A) => Directive1[A#Value] = (auth) => {
    val serialized = authenticator.serialize(auth)
    provide(serialized) & authenticator.embed(serialized)
  }

  val discard: Directive0 = extract(authenticator.retrieve).flatMap(r => onSuccess(r)).flatMap {
    case Some(a) => onComplete(authenticator.remove(a)).flatMap(r => authenticator.unembed)
    case None => pass
  }

  val authenticate: Directive1[T] = extract(authenticator.apply).flatMap(onSuccess(_)).flatMap {
    case Right((auth, user)) ⇒ provide(user) & authenticator.embed(authenticator.serialize(auth))
    case Left(rejection) ⇒ reject(rejection)
  }

  val optionalAuthenticate: Directive1[Option[T]] = extract(authenticator.apply).flatMap(onSuccess(_)).flatMap {
    case Right((auth, user)) ⇒ provide(Option(user)) & authenticator.embed(authenticator.serialize(auth))
    case Left(AuthenticationFailedRejection(CredentialsMissing, _)) ⇒ provide(None)
    case Left(rejection) ⇒ reject(rejection)
  }

}

object RorschachAuthMagnet {
  implicit def fromRorschachAuthenticator[A <: Authenticator, T <: Identity](authenticator: AkkaRorschachAuthenticator[A, T])(implicit executor: ExecutionContext): RorschachAuthMagnet[A, T] = {
    new RorschachAuthMagnet[A, T](authenticator)
  }
}

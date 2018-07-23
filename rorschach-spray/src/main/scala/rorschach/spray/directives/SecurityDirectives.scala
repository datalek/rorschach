package rorschach.spray.directives

import shapeless._
import spray.routing._
import rorschach.core.{ Authenticator, LoginInfo }
import rorschach.exceptions.{ AuthenticatorNotFoundException, IdentityNotFoundException, InvalidAuthenticatorException }
import rorschach.spray._
import spray.routing.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
import spray.routing.authentication.Authentication
import spray.routing.directives.AuthMagnet
import spray.routing.directives.BasicDirectives.{ extract, provide }
import spray.routing.directives.FutureDirectives.onSuccess
import scala.concurrent.ExecutionContext

trait SecurityDirectives {
  /**
   * Provide an authenticator from a loginInfo
   */
  def create[A <: Authenticator, T](magnet: RorschachAuthMagnet[A, T], loginInfo: LoginInfo): Directive[A :: HNil] = ???

  /**
   * Given an authenticator embed the serialized value and provide this value on inner route
   * Examples: if authenticator is a token it will embed and provided as string (serialized),
   * if is a cookie it wil be embed and provide as [[spray.http.HttpHeaders.Cookie]]...
   */
  //def embed[A <: Authenticator, T](magnet: RorschachAuthMagnet[A, T], authenticator: A): Directive1[A#Value] = ???

  /**
   * Discard authenticator if there is one, otherwise do nothing.
   * Examples: if authenticator is a token it will be remove, if is a cookie it wil be discard, etc...
   */
  def discard[A <: Authenticator, T](magnet: RorschachAuthMagnet[A, T]): Directive0 = ???

}

class RorschachAuthMagnet[A <: Authenticator, T](authenticator: SprayAuthenticationHandler.Alias[A, T])(implicit executor: ExecutionContext) {

}

object RorschachAuthMagnet {
  implicit def fromRorschachAuthenticator[A <: Authenticator, T](authenticator: SprayAuthenticationHandler.Alias[A, T])(implicit executor: ExecutionContext): RorschachAuthMagnet[A, T] = {
    new RorschachAuthMagnet[A, T](authenticator)
  }

}
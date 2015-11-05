package rorschach.spray.directives

import shapeless._
import spray.routing._
import spray.routing.Directives._
import rorschach.core.{LoginInfo, Identity, Authenticator}
import rorschach.spray.SprayRorschachAuthenticator
import scala.concurrent.ExecutionContext

trait SecurityDirectives {
  /**
   * Provide an authenticator from a loginInfo
   */
  def create[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T], loginInfo: LoginInfo): Directive[A :: HNil] = magnet.create(loginInfo)

  /**
   * Given an authenticator embed the serialized value and provide this value on inner route
   * Examples: if authenticator is a token it will embed and provided as string (serialized),
   * if is a cookie it wil be embed and provide as [[spray.http.HttpHeaders.Cookie]]...
   */
  def embed[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T], authenticator: A): Directive1[A#Value] = magnet.embed(authenticator)

  /**
   * Discard authenticator if there is one, otherwise do nothing.
   * Examples: if authenticator is a token it will be remove, if is a cookie it wil be discard, etc...
   */
  def discard[A <: Authenticator, T <: Identity](magnet: RorschachAuthMagnet[A, T]): Directive0 = magnet.discard

}

class RorschachAuthMagnet[A <: Authenticator, T <: Identity](authenticator: SprayRorschachAuthenticator[A, T])(implicit executor: ExecutionContext) {

  val create: (LoginInfo) => Directive[A :: HNil] = (loginInfo) => onSuccess(authenticator.create(loginInfo)).flatMap(a => provide(a))

  val embed: (A) => Directive1[A#Value] = (auth) => {
    val serialized = authenticator.serialize(auth)
    provide(serialized) & authenticator.embed(serialized)
  }

  val discard: Directive0 = extract(authenticator.retrieve).flatMap(r => onSuccess(r)).flatMap {
    case Some(a) => onComplete(authenticator.remove(a)).flatMap(r => authenticator.unembed)
    case None => pass
  }

}

object RorschachAuthMagnet {
  implicit def fromRorschachAuthenticator[A <: Authenticator, T <: Identity](authenticator: SprayRorschachAuthenticator[A, T])(implicit executor: ExecutionContext): RorschachAuthMagnet[A, T] = {
    new RorschachAuthMagnet[A, T](authenticator)
  }
}
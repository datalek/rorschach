package rorschach

package object akka {

  type AkkaAuthenticationHandlerChain[I] = List[AkkaAuthenticationHandler[I]]

  implicit def akkaAuthenticationHandlerToAuthenticators[I](in: AkkaAuthenticationHandler[I]) = List(in)
}

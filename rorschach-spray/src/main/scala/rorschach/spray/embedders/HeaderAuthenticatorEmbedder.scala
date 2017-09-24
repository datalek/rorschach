package rorschach.spray.embedders

import spray.http.HttpHeaders._
import spray.routing.Directives._
import spray.routing._
import rorschach.core.embedders._

object HeaderAuthenticatorEmbedder {

  def apply[A](headerName: String)(
    implicit
    authenticatorEmbedder: AuthenticatorEmbedder[A, String]
  ): AuthenticatorEmbedder[A, Directive0] = AuthenticatorEmbedder.instance { in =>
    val value = authenticatorEmbedder(in)
    respondWithHeader(RawHeader(headerName, value))
  }

}

package rorschach.akka.embedders

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.respondWithHeader
import akka.http.scaladsl.server._
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

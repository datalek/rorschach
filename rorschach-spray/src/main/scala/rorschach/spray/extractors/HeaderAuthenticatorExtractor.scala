package rorschach.spray.extractors

import spray.routing._
import rorschach.core.extractors._
import scala.concurrent._

object HeaderAuthenticatorExtractor {

  def apply[A](headerName: String)(
    implicit
    authenticatorExtractor: AuthenticatorExtractor[String, A]
  ): AuthenticatorExtractor[RequestContext, A] = AuthenticatorExtractor.instance { ctx =>
    val value = ctx.request.headers.find(httpHeader => httpHeader.name == headerName).map(_.value)
    value.map(authenticatorExtractor).getOrElse(Future.successful(None))
  }

}

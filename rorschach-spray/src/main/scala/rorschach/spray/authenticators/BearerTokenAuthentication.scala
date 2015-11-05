package rorschach.spray.authenticators

import rorschach.core.Identity
import rorschach.core.authenticators.{BearerTokenAuthenticatorService, BearerTokenAuthenticator, BearerTokenAuthenticatorSettings}
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.spray.SprayRorschachAuthenticator
import rorschach.util.IdGenerator
import spray.http.HttpHeaders
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.{Future, ExecutionContext}

class BearerTokenAuthentication[I <: Identity](
  val idGenerator: IdGenerator,
  val settings: BearerTokenAuthenticatorSettings,
  val identityService: IdentityService[I],
  val dao: AuthenticatorDao[BearerTokenAuthenticator]
  )(implicit val ec: ExecutionContext) extends SprayRorschachAuthenticator[BearerTokenAuthenticator, I] {

  protected val authenticationService = new BearerTokenAuthenticatorService(idGenerator, settings, dao)

  def retrieve(ctx: RequestContext): Future[Option[BearerTokenAuthenticator]] = {
    ctx.request.headers.find(httpHeader => httpHeader.name == settings.headerName).map(_.value).map { id =>
      dao.find(id)
    }.getOrElse(Future.successful(None))
  }

  def serialize(authenticator: BearerTokenAuthenticator): String = authenticator.id
  def embed(value: String): Directive0 = respondWithHeader(HttpHeaders.RawHeader(settings.headerName, value))

}



package rorschach.spray.authenticators

import rorschach.core.Identity
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.spray.SprayRorschachAuthenticator
import rorschach.core.authenticators.{JWTAuthenticatorSettings, JWTAuthenticatorService, JWTAuthenticator}
import rorschach.util.IdGenerator
import spray.http.HttpHeaders
import spray.routing.Directives._
import spray.routing.{RequestContext, Directive0}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class JWTAuthentication[I <: Identity](
  val idGenerator: IdGenerator,
  val settings: JWTAuthenticatorSettings,
  val identityService: IdentityService[I],
  val dao: Option[AuthenticatorDao[JWTAuthenticator]]
  )(implicit val ec: ExecutionContext) extends SprayRorschachAuthenticator[JWTAuthenticator, I] {

  protected val authenticationService = new JWTAuthenticatorService(idGenerator, settings, dao)

  def retrieve(ctx: RequestContext): Future[Option[JWTAuthenticator]] = {
    ctx.request.headers.find(httpHeader => httpHeader.name == settings.headerName).map(_.value).flatMap { jwtString =>
      JWTAuthenticator.deserialize(jwtString)(settings).toOption
    }.map(a => dao.fold(Future.successful(Option(a)))(_.find(a.id))).getOrElse(Future.successful(None))
  }

  def serialize(authenticator: JWTAuthenticator): String = JWTAuthenticator.serialize(authenticator)(settings)
  def embed(value: String): Directive0 = respondWithHeader(HttpHeaders.RawHeader(settings.headerName, value))
}
package rorschach.akka.authenticators

import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers._
import rorschach.akka.AkkaRorschachAuthenticator
import rorschach.core.Identity
import rorschach.core.authenticators.{JWTAuthenticator, JWTAuthenticatorService, JWTAuthenticatorSettings}
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.util.IdGenerator
import scala.concurrent.{ExecutionContext, Future}

class JWTAuthentication[I <: Identity](
  val idGenerator: IdGenerator,
  val settings: JWTAuthenticatorSettings,
  val identityService: IdentityService[I],
  val dao: Option[AuthenticatorDao[JWTAuthenticator]]
  )(implicit val ec: ExecutionContext) extends AkkaRorschachAuthenticator[JWTAuthenticator, I] {

  protected val authenticationService = new JWTAuthenticatorService(idGenerator, settings, dao)
  // TODO: set correct values for HttpChallenge
  override val Challenge: HttpChallenge = JWTAuthentication.Challenge

  def retrieve(ctx: RequestContext): Future[Option[JWTAuthenticator]] = {
    ctx.request.headers.find(httpHeader => httpHeader.name == settings.headerName).map(_.value).flatMap { jwtString =>
      JWTAuthenticator.unserialize(jwtString)(settings).toOption
    }.map(a => dao.fold(Future.successful(Option(a)))(_.find(a.id))).getOrElse(Future.successful(None))
  }

  def serialize(authenticator: JWTAuthenticator): String = JWTAuthenticator.serialize(authenticator)(settings)
  def embed(value: String): Directive0 = respondWithHeader(RawHeader(settings.headerName, value))
}


object JWTAuthentication {
  val Challenge: HttpChallenge = HttpChallenge(scheme = "", realm = "")
}
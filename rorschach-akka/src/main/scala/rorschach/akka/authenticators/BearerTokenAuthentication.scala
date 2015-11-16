package rorschach.akka.authenticators

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers._
import rorschach.akka.AkkaRorschachAuthenticator
import rorschach.core.Identity
import rorschach.core.authenticators.{BearerTokenAuthenticator, BearerTokenAuthenticatorService, BearerTokenAuthenticatorSettings}
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.util.IdGenerator
import scala.concurrent.{ExecutionContext, Future}

class BearerTokenAuthentication[I <: Identity](
  val idGenerator: IdGenerator,
  val settings: BearerTokenAuthenticatorSettings,
  val identityService: IdentityService[I],
  val dao: AuthenticatorDao[BearerTokenAuthenticator]
  )(implicit val ec: ExecutionContext) extends AkkaRorschachAuthenticator[BearerTokenAuthenticator, I] {

  protected val authenticationService = new BearerTokenAuthenticatorService(idGenerator, settings, dao)
  // TODO: set correct values for HttpChallenge
  override val Challenge: HttpChallenge = BearerTokenAuthentication.Challenge

  def retrieve(ctx: RequestContext): Future[Option[BearerTokenAuthenticator]] = {
    ctx.request.headers.find(httpHeader => httpHeader.name == settings.headerName).map(_.value).map { id =>
      dao.find(id)
    }.getOrElse(Future.successful(None))
  }

  def serialize(authenticator: BearerTokenAuthenticator): String = authenticator.id
  def embed(value: String): Directive0 = respondWithHeader(RawHeader(settings.headerName, value))

}

object BearerTokenAuthentication {
  val Challenge: HttpChallenge = HttpChallenge(scheme = "", realm = "")
}

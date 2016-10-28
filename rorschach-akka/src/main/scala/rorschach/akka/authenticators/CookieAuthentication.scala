package rorschach.akka.authenticators

import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers._
import rorschach.akka.AkkaRorschachAuthenticator
import rorschach.core.Identity
import rorschach.core.authenticators.{CookieAuthenticator, CookieAuthenticatorService, CookieAuthenticatorSettings}
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.util.IdGenerator
import scala.concurrent.{ExecutionContext, Future}

class CookieAuthentication[I <: Identity](
  val idGenerator: IdGenerator,
  val settings: CookieAuthenticatorSettings,
  val identityService: IdentityService[I],
  val dao: AuthenticatorDao[CookieAuthenticator]
  )(implicit val ec: ExecutionContext) extends AkkaRorschachAuthenticator[CookieAuthenticator, I] {

  protected val authenticationService = new CookieAuthenticatorService(idGenerator, settings, Some(dao))
  // TODO: set correct values for HttpChallenge
  override val Challenge: HttpChallenge = CookieAuthentication.Challenge

  def retrieve(ctx: RequestContext): Future[Option[CookieAuthenticator]] = {
    ctx.request.cookies.find(httpCookie => httpCookie.name == settings.cookieName).map(_.value).flatMap { serialized =>
      CookieAuthenticator.deserialize(serialized)(settings).map { authenticator =>
        dao.find(authenticator.id)
      }.toOption
    }.getOrElse(Future.successful(None))
  }

  def serialize(authenticator: CookieAuthenticator): String = CookieAuthenticator.serialize(authenticator)(settings)
  def embed(value: String): Directive0 = {
    val cookie = HttpCookie(
      name = settings.cookieName,
      value = value,
      expires = Some(DateTime(settings.authenticatorExpiry.toMillis)),
      maxAge = settings.cookieMaxAge.map(_.toMillis),
      domain = settings.cookieDomain,
      path = Some(settings.cookiePath),
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie,
      extension = None
    )
    setCookie(cookie)
  }
  override def unembed: Directive0 = deleteCookie(settings.cookieName)
}


object CookieAuthentication {
  val Challenge: HttpChallenge = HttpChallenge(scheme = "", realm = "")
}

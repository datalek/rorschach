package rorschach.spray.authenticators

import rorschach.core.Identity
import rorschach.core.authenticators.{CookieAuthenticatorService, CookieAuthenticator, CookieAuthenticatorSettings}
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.spray.SprayRorschachAuthenticator
import rorschach.util.IdGenerator
import spray.http.{DateTime, HttpCookie, HttpHeaders}
import spray.routing.{Directive0, RequestContext}
import spray.routing.Directives._

import scala.concurrent.{Future, ExecutionContext}

class CookieAuthentication[I <: Identity](
  val idGenerator: IdGenerator,
  val settings: CookieAuthenticatorSettings,
  val identityService: IdentityService[I],
  val dao: AuthenticatorDao[CookieAuthenticator]
  )(implicit val ec: ExecutionContext) extends SprayRorschachAuthenticator[CookieAuthenticator, I] {

  protected val authenticationService = new CookieAuthenticatorService(idGenerator, settings, Some(dao))

  def retrieve(ctx: RequestContext): Future[Option[CookieAuthenticator]] = {
    ctx.request.cookies.find(httpCookie => httpCookie.name == settings.cookieName).map(_.value).map { id =>
      dao.find(id)
    }.getOrElse(Future.successful(None))
  }

  def serialize(authenticator: CookieAuthenticator): String = authenticator.id
  def embed(value: String): Directive0 = {
    val cookie = HttpCookie(
      name = settings.cookieName,
      content = value,
      expires = settings.cookieMaxAge.map(d => DateTime(d.toMillis)),
      maxAge = settings.cookieMaxAge.map(_.toMillis),
      domain = settings.cookieDomain,
      path = Some(settings.cookiePath),
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie,
      extension = None
    )
    setCookie(cookie)
  }
  override def unembed: Directive0 = optionalCookie(settings.cookieName).flatMap {
    case Some(cookie) => deleteCookie(cookie)
    case None => pass
  }
}

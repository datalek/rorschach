package rorschach.spray.authenticators

import scala.concurrent.Future
import scala.concurrent.duration._
import org.joda.time.DateTime
import org.specs2.time.NoTimeConversions
import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import spray.routing.{RejectionHandler, AuthenticationFailedRejection}
import spray.routing.AuthenticationFailedRejection.{CredentialsRejected, CredentialsMissing}
import spray.http.{HttpCookie, StatusCodes}
import spray.http.HttpHeaders._
import spray.routing.Directives._
import spray.testkit.Specs2RouteTest
import rorschach.core._
import rorschach.core.authenticators._
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.spray.Directives._
import rorschach.util.IdGenerator

class CookieAuthenticationSpec extends Specification with Specs2RouteTest with NoTimeConversions {

  "the 'create(SprayRorschachAuthenticator, loginInfo)' directive" should {
    "create a valid authenticator" in new Context {
      (idGenerator.generate _).expects().returns(Future.successful(cookie.id))
      (dao.add _).expects(*).returns(Future.successful(cookie))
      Get() ~> create(authenticator, loginInfo) { auth =>
        embed(authenticator, auth) { serialized => complete("you are in!") }
      } ~> check {
        // TODO: check the whole cookie
        header[`Set-Cookie`].map(_.cookie.name) === Some(settings.cookieName)
      }
    }
  }
  "the 'discard(SprayRorschachAuthenticator)' directive" should {
    "unembed an authenticator and remove from store" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (dao.remove _).expects(cookie.id).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(HttpCookie(settings.cookieName, serialized))) ~> discard(authenticator) {
        complete("logout done")
      } ~> check {
        header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie(settings.cookieName, "deleted", expires = deletedTimeStamp, domain = settings.cookieDomain)))
      }
    }
    "responds without error if no authenticator is found" in new Context {
      Get() ~> discard(authenticator) {
        complete("logout done")
      } ~> check {
        status === StatusCodes.OK
      }
    }
  }
  "the 'authenticate(SprayRorschachAuthenticator)' directive" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in new Context {
      Get() ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection === AuthenticationFailedRejection(CredentialsMissing, List()) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(cookie.id).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(HttpCookie(settings.cookieName, serialized))) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection === AuthenticationFailedRejection(CredentialsRejected, List()) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Some(invalidCookie)))
      (dao.remove _).expects(invalidCookie.id).returns(Future.successful(invalidCookie))
      Get().withHeaders(Cookie(HttpCookie(settings.cookieName, serialized))) ~> handleRejections(RejectionHandler.Default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status === StatusCodes.Unauthorized
      }
    }
    "reject requests with illegal Authorization header with 401" in new Context {
      Get().withHeaders(Cookie(HttpCookie(settings.cookieName + "invalid", serialized))) ~> handleRejections(RejectionHandler.Default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status === StatusCodes.Unauthorized and
          responseAs[String] === "The resource requires authentication, which was not supplied with the request"
      }
    }
    "extract the object representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(HttpCookie(settings.cookieName, serialized))) ~> authenticate(authenticator) { user =>
        complete(user.username)
      } ~> check {
        responseAs[String] === loginInfo.providerKey
      }
    }
  }

  case class User(username: String) extends Identity

  trait Context extends MockContext {
    val loginInfo = LoginInfo("providerId", "thisisanemail@email.com")
    val user = User(loginInfo.providerKey)
    val settings = CookieAuthenticatorSettings(cookieMaxAge = Some(1.day))
    val cookie = CookieAuthenticator("id", loginInfo, DateTime.now, DateTime.now.plusHours(1), Some(10.minutes), None)
    val invalidCookie= CookieAuthenticator("id", loginInfo, DateTime.now.minusHours(2), DateTime.now.minusHours(1), Some(10.minutes), None)
    val serialized = CookieAuthenticator.serialize(cookie)(settings)
    val deletedTimeStamp = spray.http.DateTime.fromIsoDateTimeString("1800-01-01T00:00:00")
//    val httpCookie = HttpCookie(
//      name = settings.cookieName,
//      content = serialized,
//      expires = settings.cookieMaxAge.map(d => spray.http.DateTime(d.toMillis)),
//      maxAge = settings.cookieMaxAge.map(_.toMillis),
//      domain = settings.cookieDomain,
//      path = Some(settings.cookiePath),
//      secure = settings.secureCookie,
//      httpOnly = settings.httpOnlyCookie,
//      extension = None
//    )

    /* create the mock */
    val idGenerator = mock[IdGenerator]
    val identityService = mock[IdentityService[User]]
    val dao = mock[AuthenticatorDao[CookieAuthenticator]]

    /* service under test */
    val authenticator = new CookieAuthentication[User](
      idGenerator = idGenerator,
      settings = settings,
      identityService = identityService,
      dao = dao)
  }

}

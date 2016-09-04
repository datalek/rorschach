package rorschach.akka.authenticators

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.AuthenticationFailedRejection._
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import rorschach.core._
import rorschach.core.authenticators._
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.akka.Directives._
import rorschach.util.IdGenerator
import scala.concurrent.Future
import scala.concurrent.duration._

class CookieAuthenticationSpec extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

  "the 'create(SprayRorschachAuthenticator, loginInfo)' directive" should {
    "create a valid authenticator" in new Context {
      (idGenerator.generate _).expects().returns(Future.successful(cookie.id))
      (dao.add _).expects(*).returns(Future.successful(cookie))
      Get() ~> create(authenticator, loginInfo) { auth =>
        embed(authenticator, auth) { serialized => complete("you are in!") }
      } ~> check {
        // TODO: check the whole cookie
        header[`Set-Cookie`].map(_.cookie.name) shouldEqual Some(settings.cookieName)
      }
    }
  }
  "the 'discard(SprayRorschachAuthenticator)' directive" should {
    "unembed an authenticator and remove from store" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (dao.remove _).expects(cookie.id).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> discard(authenticator) {
        complete("logout done")
      } ~> check {
        header[`Set-Cookie`] shouldEqual Some(`Set-Cookie`(HttpCookie(settings.cookieName, "deleted", expires = deletedTimeStamp, domain = settings.cookieDomain)))
      }
    }
    "responds without error if no authenticator is found" in new Context {
      Get() ~> discard(authenticator) {
        complete("logout done")
      } ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
  "the 'authenticate(SprayRorschachAuthenticator)' directive" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in new Context {
      Get() ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, CookieAuthentication.Challenge) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(cookie.id).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, CookieAuthentication.Challenge) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Some(invalidCookie)))
      (dao.remove _).expects(invalidCookie.id).returns(Future.successful(invalidCookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> handleRejections(RejectionHandler.default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
    "reject requests with illegal Authorization header with 401" in new Context {
      Get().withHeaders(Cookie(settings.cookieName + "invalid", serialized)) ~> handleRejections(RejectionHandler.default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status shouldEqual StatusCodes.Unauthorized
          responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
      }
    }
    "extract the object representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> authenticate(authenticator) { user =>
        complete(user.username)
      } ~> check {
        responseAs[String] shouldEqual loginInfo.providerKey
      }
    }
  }
  "the 'optionalAuthenticate(SprayRorschachAuthenticator)' directive" should {
    "extract None from requests without Authorization header" in new Context {
      Get() ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "extract None from requests with Authorization header using a different scheme" in new Context {
      Get().withHeaders(Cookie(settings.cookieName + "different", serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(cookie.id).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, JWTAuthentication.Challenge) }
    }
    "extract Some(object) representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.map(_.username)) }
      } ~> check { responseAs[String] shouldEqual user.username }
    }
    "properly handle exceptions thrown in its inner route" in new Context {
      (dao.find _).expects(cookie.id).returns(Future.successful(Option(cookie)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(cookie))
      Get().withHeaders(Cookie(settings.cookieName, serialized)) ~> {
        handleExceptions(ExceptionHandler.default(RoutingSettings.default)) {
          optionalAuthenticate(authenticator) { _ ⇒ throw new Exception }
        }
      } ~> check { status shouldEqual StatusCodes.InternalServerError }
    }
  }

  case class User(username: String) extends Identity

  trait Context {
    val loginInfo = LoginInfo("providerId", "thisisanemail@email.com")
    val user = User(loginInfo.providerKey)
    val settings = CookieAuthenticatorSettings(cookieMaxAge = Some(1.day))
    val cookie = CookieAuthenticator("id", loginInfo, DateTime.now, DateTime.now.plusHours(1), Some(10.minutes), None)
    val invalidCookie= CookieAuthenticator("id", loginInfo, DateTime.now.minusHours(2), DateTime.now.minusHours(1), Some(10.minutes), None)
    val serialized = CookieAuthenticator.serialize(cookie)(settings)
    val deletedTimeStamp = akka.http.scaladsl.model.DateTime.fromIsoDateTimeString("1800-01-01T00:00:00")
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

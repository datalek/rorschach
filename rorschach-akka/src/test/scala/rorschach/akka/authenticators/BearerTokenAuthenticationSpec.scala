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

class BearerTokenAuthenticationSpec extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

  "the 'create(SprayRorschachAuthenticator, loginInfo)' directive" should {
    "create a valid authenticator" in new Context {
      (idGenerator.generate _).expects().returns(Future.successful(bearerToken.id))
      (dao.add _).expects(*).returns(Future.successful(bearerToken))
      Get() ~> create(authenticator, loginInfo) { auth =>
        embed(authenticator, auth) { serialized => complete("you are in!") }
      } ~> check {
        header(settings.headerName) shouldEqual Some(RawHeader(settings.headerName, serialized))
      }
    }
  }
  "the 'discard(SprayRorschachAuthenticator)' directive" should {
    "unembed an authenticator and remove from store" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Option(bearerToken)))
      (dao.remove _).expects(bearerToken.id).returns(Future.successful(bearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> discard(authenticator) {
        complete("logout done")
      } ~> check {
        header(settings.headerName) shouldEqual None
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
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, BearerTokenAuthentication.Challenge) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Option(bearerToken)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(bearerToken.id).returns(Future.successful(bearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, BearerTokenAuthentication.Challenge) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Some(invalidBearerToken)))
      (dao.remove _).expects(invalidBearerToken.id).returns(Future.successful(invalidBearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> handleRejections(RejectionHandler.default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
    "reject requests with illegal Authorization header with 401" in new Context {
      Get().withHeaders(RawHeader(settings.headerName + "notValid", "bob alice")) ~> handleRejections(RejectionHandler.default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status shouldEqual StatusCodes.Unauthorized
          responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
      }
    }
    "extract the object representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Option(bearerToken)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(bearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> authenticate(authenticator) { user =>
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
      Get().withHeaders(RawHeader(settings.headerName + "different", serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Option(bearerToken)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(bearerToken.id).returns(Future.successful(bearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, JWTAuthentication.Challenge) }
    }
    "extract Some(object) representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Option(bearerToken)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(bearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.map(_.username)) }
      } ~> check { responseAs[String] shouldEqual user.username }
    }
    "properly handle exceptions thrown in its inner route" in new Context {
      (dao.find _).expects(bearerToken.id).returns(Future.successful(Option(bearerToken)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(bearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
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
    val bearerToken = BearerTokenAuthenticator("id", loginInfo, DateTime.now, DateTime.now.plusHours(1), Some(10.minutes))
    val invalidBearerToken = BearerTokenAuthenticator("id", loginInfo, DateTime.now.minusHours(2), DateTime.now.minusHours(1), Some(10.minutes))
    val serialized = bearerToken.id

    /* create the mock */
    val idGenerator = mock[IdGenerator]
    val identityService = mock[IdentityService[User]]
    val dao = mock[AuthenticatorDao[BearerTokenAuthenticator]]

    val settings = BearerTokenAuthenticatorSettings(headerName = "X-Authentication")

    /* service under test */
    val authenticator = new BearerTokenAuthentication[User](
      idGenerator = idGenerator,
      settings = settings,
      identityService = identityService,
      dao = dao)
  }

}

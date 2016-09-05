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
import org.scalatest.{WordSpec, Matchers}
import rorschach.core._
import rorschach.akka.Directives._
import rorschach.core.authenticators._
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.util.IdGenerator
import scala.concurrent.Future
import scala.concurrent.duration._

class JWTAuthenticationSpec extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

  "the 'create(SprayRorschachAuthenticator, loginInfo)' directive" should {
    "create a valid authenticator" in new Context {
      (idGenerator.generate _).expects().returns(Future.successful(token.id))
      (dao.add _).expects(*).returns(Future.successful(token))
      Get() ~> create(authenticator, loginInfo) { auth =>
        embed(authenticator, auth) { serialized => complete("you are in!") }
      } ~> check {
        // TODO: check the serialized value
        header(settings.headerName) !== None//Some(RawHeader(settings.headerName, serialized))
      }
    }
  }
  "the 'discard(SprayRorschachAuthenticator)' directive" should {
    "unembed an authenticator and remove from store" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (dao.remove _).expects(token.id).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> discard(authenticator) {
        complete("logout done")
      } ~> check {
        header(settings.headerName) shouldEqual None
      }
    }
    "unembed an authenticator and doesn't remove from store" in new Context {
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> discard(authenticatorWithoutStore) {
        complete("logout done")
      } ~> check {
        header(settings.headerName) shouldEqual None
      }
    }
  }
  "the 'authenticate(SprayRorschachAuthenticator)' directive" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in new Context {
      Get() ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, JWTAuthentication.Challenge) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(token.id).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, JWTAuthentication.Challenge) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Some(invalidBearerToken)))
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
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(token))
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
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(token.id).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, JWTAuthentication.Challenge) }
    }
    "extract Some(object) representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.map(_.username)) }
      } ~> check { responseAs[String] shouldEqual user.username }
    }
    "properly handle exceptions thrown in its inner route" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(token))
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
    val settings = JWTAuthenticatorSettings(headerName = "X-Authentication", issuerClaim = "this is an issuer man!", sharedSecret = "shhhhhhhhhh!!")
    val token = JWTAuthenticator("id", loginInfo, DateTime.now, DateTime.now.plusHours(1), Some(10.minutes))
    val invalidBearerToken = JWTAuthenticator("id", loginInfo, DateTime.now.minusHours(2), DateTime.now.minusHours(1), Some(10.minutes))
    val serialized = JWTAuthenticator.serialize(token)(settings)

    /* create the mock */
    val idGenerator = mock[IdGenerator]
    val identityService = mock[IdentityService[User]]
    val dao = mock[AuthenticatorDao[JWTAuthenticator]]

    /* service under test */
    val authenticator = new JWTAuthentication[User](
      idGenerator = idGenerator,
      settings = settings,
      identityService = identityService,
      dao = Some(dao))
    val authenticatorWithoutStore = new JWTAuthentication[User](
      idGenerator = idGenerator,
      settings = settings,
      identityService = identityService,
      dao = None)
  }

}

package rorschach.spray.authenticators

import scala.concurrent.Future
import scala.concurrent.duration._
import org.joda.time.DateTime
import org.specs2.time.NoTimeConversions
import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import spray.routing.{RejectionHandler, AuthenticationFailedRejection}
import spray.routing.AuthenticationFailedRejection.{CredentialsRejected, CredentialsMissing}
import spray.http.StatusCodes
import spray.http.HttpHeaders._
import spray.routing.Directives._
import spray.testkit.Specs2RouteTest
import rorschach.core._
import rorschach.core.authenticators._
import rorschach.core.daos.AuthenticatorDao
import rorschach.core.services.IdentityService
import rorschach.spray.Directives._
import rorschach.util.IdGenerator

class JWTAuthenticationSpec extends Specification with Specs2RouteTest with NoTimeConversions {

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
        header(settings.headerName) === None
      }
    }
    "unembed an authenticator and doesn't remove from store" in new Context {
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> discard(authenticatorWithoutStore) {
        complete("logout done")
      } ~> check {
        header(settings.headerName) === None
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
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(None))
      (dao.remove _).expects(token.id).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection === AuthenticationFailedRejection(CredentialsRejected, List()) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Some(invalidBearerToken)))
      (dao.remove _).expects(invalidBearerToken.id).returns(Future.successful(invalidBearerToken))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> handleRejections(RejectionHandler.Default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status === StatusCodes.Unauthorized
      }
    }
    "reject requests with illegal Authorization header with 401" in new Context {
      Get().withHeaders(RawHeader(settings.headerName + "notValid", "bob alice")) ~> handleRejections(RejectionHandler.Default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status === StatusCodes.Unauthorized and
          responseAs[String] === "The resource requires authentication, which was not supplied with the request"
      }
    }
    "extract the object representing the user identity created by successful authentication" in new Context {
      (dao.find _).expects(token.id).returns(Future.successful(Option(token)))
      (identityService.retrieve _).expects(loginInfo).returns(Future.successful(Option(user)))
      (dao.update _).expects(*).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> authenticate(authenticator) { user =>
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

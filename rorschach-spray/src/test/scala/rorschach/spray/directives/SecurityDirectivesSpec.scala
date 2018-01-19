package rorschach.spray.directives

import java.time.Instant
import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import rorschach.core._
import rorschach.core.embedders._
import rorschach.core.extractors._
import rorschach.core.services._
import rorschach.core.authenticators._
import rorschach.spray._
import rorschach.spray.Directives._
import rorschach.spray.embedders.HeaderAuthenticatorEmbedder
import rorschach.spray.extractors.HeaderAuthenticatorExtractor
import spray.http.HttpHeaders._
import spray.http.StatusCodes
import spray.routing.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
import spray.routing.Directives._
import spray.routing.{ AuthenticationFailedRejection, RejectionHandler }
import spray.testkit.Specs2RouteTest
import scala.concurrent.Future
import scala.concurrent.duration._

class SecurityDirectivesSpec extends Specification with Specs2RouteTest with NoTimeConversions {

  //  "the 'create(SprayRorschachAuthenticator, loginInfo)' directive" should {
  //    "create a valid authenticator" in new Context {
  //      //(idGenerator.generate _).expects().returns(Future.successful(token.id))
  //      //(dao.add _).expects(*).returns(Future.successful(token))
  //      Get() ~> create(authenticator, loginInfo) { auth =>
  //        embed(authenticator, auth) { serialized => complete("you are in!") }
  //      } ~> check {
  //        // TODO: check the serialized value
  //        header(settings.headerName) !== None //Some(RawHeader(settings.headerName, serialized))
  //      }
  //    }.pendingUntilFixed("NOT YET IMPLEMENTED")
  //  }
  //  "the 'discard(SprayRorschachAuthenticator)' directive" should {
  //    "unembed an authenticator and remove from store" in new Context {
  //      //(dao.find _).expects(token.id).returns(Future.successful(Option(token)))
  //      //(dao.remove _).expects(token.id).returns(Future.successful(token))
  //      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> discard(authenticator) {
  //        complete("logout done")
  //      } ~> check {
  //        header(settings.headerName) === None
  //      }
  //    }.pendingUntilFixed("NOT YET IMPLEMENTED")
  //    "unembed an authenticator and doesn't remove from store" in new Context {
  //      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> discard(authenticatorWithoutStore) {
  //        complete("logout done")
  //      } ~> check {
  //        header(settings.headerName) === None
  //      }
  //    }.pendingUntilFixed("NOT YET IMPLEMENTED")
  //  }
  "the 'authenticate(SprayRorschachAuthenticator)' directive" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in new Context {
      Get() ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection === AuthenticationFailedRejection(CredentialsMissing, List()) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(None))
      (authenticatorService.remove _).expects(*).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection === AuthenticationFailedRejection(CredentialsRejected, List()) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (authenticatorService.remove _).expects(*).returns(Future.successful(invalidBearerToken))
      Get().withHeaders(RawHeader(settings.headerName, invalidSerialized)) ~> handleRejections(RejectionHandler.Default) {
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
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(Option(user)))
      (authenticatorService.touch _).expects(*).returns(Right(token))
      (authenticatorService.update _).expects(token).returns(Future.successful(token))
      Get().withHeaders(RawHeader(settings.headerName, serialized)) ~> authenticate(authenticator) { user =>
        complete(user.username)
      } ~> check {
        responseAs[String] === loginInfo.providerKey
      }
    }
  }

  case class User(username: String)

  trait Context extends MockContext {
    val loginInfo = LoginInfo("providerId", "thisisanemail@email.com")
    val user = User(loginInfo.providerKey)
    val settings = JWTAuthenticatorSettings(headerName = "X-Authentication", issuerClaim = "this is an issuer man!", sharedSecret = "shhhhhhhhhh!!")
    val token = JWTAuthenticator("id", loginInfo, Instant.now, Instant.now.plusHours(1), Some(10.minutes))
    val invalidBearerToken = JWTAuthenticator("id", loginInfo, Instant.now.minusHours(2), Instant.now.minusHours(1), Some(10.minutes))
    val serialized = JWTAuthenticator.serialize(token)(settings)
    val invalidSerialized = JWTAuthenticator.serialize(invalidBearerToken)(settings)

    /* create the mock */
    val authenticatorExtractor = HeaderAuthenticatorExtractor(settings.headerName)(JwtAuthenticatorExtractor(settings)) //mock[AuthenticatorExtractor[RequestContext, JWTAuthenticator]]
    val authenticatorService = mock[AuthenticatorService[JWTAuthenticator]]
    val identityExtractor = mock[IdentityRetriever[LoginInfo, User]]
    val authenticatorEmbedder = HeaderAuthenticatorEmbedder(settings.headerName)(JwtAuthenticatorEmbedder(settings))

    /* service under test */
    val authenticator = SprayAuthenticationHandler.fromRorschachAuthenticator(SprayAuthenticationHandler(
      authenticatorExtractor,
      authenticatorService,
      identityExtractor,
      authenticatorEmbedder
    ))
    val authenticatorWithoutStore = SprayAuthenticationHandler.fromRorschachAuthenticator(SprayAuthenticationHandler(
      authenticatorExtractor,
      authenticatorService,
      identityExtractor,
      authenticatorEmbedder
    ))
  }

}

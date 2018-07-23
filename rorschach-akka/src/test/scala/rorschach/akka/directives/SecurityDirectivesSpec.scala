package rorschach.akka.directives

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.AuthenticationFailedRejection._
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }
import rorschach.akka._
import rorschach.core._
import rorschach.core.services._
import rorschach.akka.Directives._
import rorschach.akka.embedders._
import rorschach.akka.extractors._
import rorschach.core.embedders.AuthenticatorEmbedder
import rorschach.core.extractors.AuthenticatorExtractor
import scala.concurrent.Future

class SecurityDirectivesSpec extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

//  "the 'create(SprayRorschachAuthenticator, loginInfo)' directive" should {
//    "create a valid authenticator" in new Context {
//      //(idGenerator.generate _).expects().returns(Future.successful(token.id))
//      //(dao.add _).expects(*).returns(Future.successful(token))
//      Get() ~> create(authenticator, loginInfo) { auth =>
//        embed(authenticator, auth) { serialized => complete("you are in!") }
//      } ~> check {
//        // TODO: check the serialized value
//        header(headerName) !== None //Some(RawHeader(headerName, serialized))
//      }
//    }
//  }
//  "the 'discard(SprayRorschachAuthenticator)' directive" should {
//    "unembed an authenticator and remove from store" in new Context {
//      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
//      (authenticatorService.remove _).expects(*).returns(Future.successful(auth))
//      Get().withHeaders(RawHeader(headerName, token)) ~> discard(authenticator) {
//        complete("logout done")
//      } ~> check {
//        header(headerName) shouldEqual None
//      }
//    }
//  }
  "the 'authenticate(AuthenticationHandler)' directive" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in new Context {
      Get() ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, challenge) }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(None))
      (authenticatorService.remove _).expects(*).returns(Future.successful(auth))
      Get().withHeaders(RawHeader(headerName, token)) ~> {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, challenge) }
    }
    "reject requests with invalid authenticator with 401" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(invalidToken).returns(Future(Option(invalidAuth)))
      (authenticatorService.remove _).expects(*).returns(Future.successful(invalidAuth))
      Get().withHeaders(RawHeader(headerName, invalidToken)) ~> handleRejections(RejectionHandler.default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
    "reject requests with illegal Authorization header with 401" in new Context {
      Get().withHeaders(RawHeader(headerName + "notValid", "bob alice")) ~> handleRejections(RejectionHandler.default) {
        authenticate(authenticator) { user => complete(user.username) }
      } ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
      }
    }
    "extract the object representing the user identity created by successful authentication" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(Option(user)))
      (authenticatorService.touch _).expects(*).returns(Right(auth))
      (authenticatorService.update _).expects(auth).returns(Future.successful(auth))
      (toStringAuthenticatorEmbedder.apply _).expects(auth).returns(token)
      Get().withHeaders(RawHeader(headerName, token)) ~> authenticate(authenticator) { user =>
        complete(user.username)
      } ~> check {
        responseAs[String] shouldEqual loginInfo.providerKey
      }
    }
  }
  "the 'authenticate(AuthenticationHandlerChain)' directive" should {
    "reject requests without Authorization header with an AuthenticationFailedRejection" in new Context {
      Get() ~> {
        authenticate(authenticators) { user => complete(user.username) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsMissing, challenge) }
    }
    "handle exception if authenticators list is empty" in new Context {
      Get() ~> Route.seal {
        authenticate(emptyAuthenticators) { user => complete(user.username) }
      } ~> check { status shouldEqual StatusCodes.InternalServerError }
    }
    "extract the object representing the user identity created by successful authentication" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(Option(user)))
      (authenticatorService.touch _).expects(*).returns(Right(auth))
      (authenticatorService.update _).expects(auth).returns(Future.successful(auth))
      (toStringAuthenticatorEmbedder.apply _).expects(auth).returns(token)
      Get().withHeaders(RawHeader(headerName, token)) ~> authenticate(authenticators) { user =>
        complete(user.username)
      } ~> check {
        responseAs[String] shouldEqual loginInfo.providerKey
      }
    }
  }
  "the 'optionalAuthenticate(AuthenticationHandler)' directive" should {
    "extract None from requests without Authorization header" in new Context {
      Get() ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "extract None from requests with Authorization header using a different scheme" in new Context {
      Get().withHeaders(RawHeader(headerName + "different", token)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "reject unauthenticated requests with Authorization header with an AuthenticationFailedRejection" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(None))
      (authenticatorService.remove _).expects(*).returns(Future.successful(auth))
      Get().withHeaders(RawHeader(headerName, token)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.toString) }
      } ~> check { rejection shouldEqual AuthenticationFailedRejection(CredentialsRejected, challenge) }
    }
    "extract Some(object) representing the user identity created by successful authentication" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(Option(user)))
      (authenticatorService.touch _).expects(*).returns(Right(auth))
      (authenticatorService.update _).expects(auth).returns(Future.successful(auth))
      (toStringAuthenticatorEmbedder.apply _).expects(auth).returns(token)
      Get().withHeaders(RawHeader(headerName, token)) ~> {
        optionalAuthenticate(authenticator) { user => complete(user.map(_.username)) }
      } ~> check { responseAs[String] shouldEqual user.username }
    }
    "properly handle exceptions thrown in its inner route" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(Option(user)))
      (authenticatorService.touch _).expects(*).returns(Right(auth))
      (authenticatorService.update _).expects(*).returns(Future.successful(auth))
      (toStringAuthenticatorEmbedder.apply _).expects(auth).returns(token)
      Get().withHeaders(RawHeader(headerName, token)) ~> {
        handleExceptions(ExceptionHandler.default(RoutingSettings.default)) {
          optionalAuthenticate(authenticator) { _ ⇒ throw new Exception }
        }
      } ~> check { status shouldEqual StatusCodes.InternalServerError }
    }
  }
  "the 'optionalAuthenticate(AuthenticationHandlerChain)' directive" should {
    "extract None from requests without Authorization header" in new Context {
      Get() ~> {
        optionalAuthenticate(authenticators) { user => complete(user.toString) }
      } ~> check { responseAs[String] shouldEqual "None" }
    }
    "handle exception if authenticators list is empty" in new Context {
      Get() ~> Route.seal {
        authenticate(emptyAuthenticators) { user => complete(user.username) }
      } ~> check { status shouldEqual StatusCodes.InternalServerError }
    }
    "properly handle exceptions thrown in its inner route" in new Context {
      (fromStringAuthenticatorExtractor.apply _).expects(token).returns(Future(Option(auth)))
      (identityExtractor.apply _).expects(loginInfo).returns(Future.successful(Option(user)))
      (authenticatorService.touch _).expects(*).returns(Right(auth))
      (authenticatorService.update _).expects(*).returns(Future.successful(auth))
      (toStringAuthenticatorEmbedder.apply _).expects(auth).returns(token)
      Get().withHeaders(RawHeader(headerName, token)) ~> {
        handleExceptions(ExceptionHandler.default(RoutingSettings.default)) {
          optionalAuthenticate(authenticators) { _ ⇒ throw new Exception }
        }
      } ~> check { status shouldEqual StatusCodes.InternalServerError }
    }
  }

  case class User(username: String)
  case class FakeAuthenticator(
    loginInfo: LoginInfo,
    isValid: Boolean
  ) extends rorschach.core.Authenticator

  trait Context {
    val loginInfo = LoginInfo("providerId", "thisisanemail@email.com")
    val user = User(loginInfo.providerKey)
    val headerName = "X-Authentication"
    val auth = FakeAuthenticator(loginInfo, isValid = true)
    val invalidAuth = FakeAuthenticator(loginInfo, isValid = false)
    val token = "serialized"
    val invalidToken = "invalid"
    val challenge = HttpChallenge(scheme = "", realm = "")
    /* create the mock */
    val fromStringAuthenticatorExtractor = mock[AuthenticatorExtractor[String, FakeAuthenticator]]
    val authenticatorExtractor = HeaderAuthenticatorExtractor(headerName)(fromStringAuthenticatorExtractor)
    val authenticatorService = mock[AuthenticatorService[FakeAuthenticator]]
    val identityExtractor = mock[IdentityRetriever[LoginInfo, User]]
    val toStringAuthenticatorEmbedder = mock[AuthenticatorEmbedder[FakeAuthenticator, String]]
    val authenticatorEmbedder = HeaderAuthenticatorEmbedder(headerName)(toStringAuthenticatorEmbedder)

    /* service under test */
    val authenticator = AkkaAuthenticationHandler(
      authenticatorExtractor,
      authenticatorService,
      identityExtractor,
      authenticatorEmbedder
    )
    val authenticatorWithoutStore = AkkaAuthenticationHandler(
      authenticatorExtractor,
      authenticatorService,
      identityExtractor,
      authenticatorEmbedder
    )
    val authenticators = authenticator :: authenticator
    val emptyAuthenticators: AkkaAuthenticationHandlerChain[User] = Nil
  }

}


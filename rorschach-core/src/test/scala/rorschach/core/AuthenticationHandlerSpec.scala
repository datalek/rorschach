package rorschach.core

import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import rorschach.core.embedders.AuthenticatorEmbedder
import rorschach.core.extractors._
import rorschach.core.services._
import rorschach.exceptions._
import test.util._
import scala.concurrent.Future

class AuthenticationHandlerSpec extends Specification {

  "the method authenticate" should {
    "return a left with AuthenticatorNotFoundException when no authenticator is retrieve from context" >> new Context {
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      (authenticatorExtractorMock.apply _).expects(request).returns(Future.successful(None))
      await(authenticationHandler.authenticate(request)) should beLeft.like {
        case e => e.isInstanceOf[AuthenticatorNotFoundException]
      }
    }

    "return a left with InvalidAuthenticatorException when the authenticator isn't valid" >> new Context {
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = false)
      (authenticatorExtractorMock.apply _).expects(request).returns(Future.successful(Some(auth.copy(isValid = false))))
      (authenticatorServiceMock.remove _).expects(auth).returns(Future.successful(auth))
      await(authenticationHandler.authenticate(request)) should beLeft.like {
        case e => e.isInstanceOf[InvalidAuthenticatorException]
      }
    }

    "return a left with IdentityNotFoundException when the authenticator isn't valid" >> new Context {
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      (authenticatorExtractorMock.apply _).expects(request).returns(Future.successful(Some(auth)))
      (identityRetrieverMock.apply _).expects(auth.loginInfo).returns(Future.successful(None))
      (authenticatorServiceMock.remove _).expects(auth).returns(Future.successful(auth))
      await(authenticationHandler.authenticate(request)) should beLeft.like {
        case e => e.isInstanceOf[IdentityNotFoundException]
      }
    }

    "return a right with user when the authenticator is valid - no dao update if authenticator is untouched" >> new Context {
      val user = DummyUser("my.username")
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      val result = (auth, user, response)
      (authenticatorExtractorMock.apply _).expects(request).returns(Future.successful(Some(auth)))
      (identityRetrieverMock.apply _).expects(auth.loginInfo).returns(Future.successful(Some(user)))
      (authenticatorServiceMock.touch _).expects(auth).returns(Left(auth))
      (authenticatorEmbedderMock.apply _).expects(auth).returns(response)
      await(authenticationHandler.authenticate(request)) should beRight(result)
    }

    "return a right with user when the authenticator is valid - with dao update if authenticator is touched" >> new Context {
      val user = DummyUser("my.username")
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      val result = (auth, user, response)
      (authenticatorExtractorMock.apply _).expects(request).returns(Future.successful(Some(auth)))
      (identityRetrieverMock.apply _).expects(auth.loginInfo).returns(Future.successful(Some(user)))
      (authenticatorServiceMock.touch _).expects(auth).returns(Right(auth))
      (authenticatorServiceMock.update _).expects(auth).returns(Future.successful(auth))
      (authenticatorEmbedderMock.apply _).expects(auth).returns(response)
      await(authenticationHandler.authenticate(request)) should beRight(result)
    }
  }

  case class DummyAuthenticator(loginInfo: LoginInfo, isValid: Boolean) extends Authenticator {
    type Value = this.type
    type Settings = this.type
  }

  case class DummyUser(username: String)

  trait Context extends MockContext with Common {
    implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

    type Request = String
    type Response = String
    val (request, response) = ("the request", "the response")

    val authenticatorExtractorMock = mock[AuthenticatorExtractor[Request, DummyAuthenticator]]
    val authenticatorServiceMock = mock[AuthenticatorService[DummyAuthenticator]]
    val identityRetrieverMock = mock[IdentityRetriever[LoginInfo, DummyUser]]
    val authenticatorEmbedderMock = mock[AuthenticatorEmbedder[DummyAuthenticator, Response]]

    /* subjects under tests */
    val authenticationHandler = AuthenticationHandler(
      authenticatorExtractor = authenticatorExtractorMock,
      authenticatorService = authenticatorServiceMock,
      identityExtractor = identityRetrieverMock,
      authenticatorEmbedder = authenticatorEmbedderMock
    )

  }

}


package rorschach.core

import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import rorschach.core.services.{AuthenticatorService, IdentityService}
import rorschach.exceptions._
import test.util.Common
import scala.concurrent.Future


class RorschachAuthenticatorSpec extends Specification with Common {

  "the method authenticate" should {
    "return a left with AuthenticatorNotFoundException when no authenticator is retrieve from context" >> new Context {
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      retrieveMock.expects(auth).returns(Future.successful(None))
      await(authenticator.authenticate(auth)) should beLeft.like {
        case e => e.isInstanceOf[AuthenticatorNotFoundException]
      }
    }

    "return a left with InvalidAuthenticatorException when the authenticator isn't valid" >> new Context {
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = false)
      retrieveMock.expects(auth).returns(Future.successful(Some(auth.copy(isValid = false))))
      (authenticatorServiceMock.remove _).expects(auth).returns(Future.successful(auth))
      await(authenticator.authenticate(auth)) should beLeft.like {
        case e => e.isInstanceOf[InvalidAuthenticatorException]
      }
    }

    "return a left with IdentityNotFoundException when the authenticator isn't valid" >> new Context {
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      retrieveMock.expects(auth).returns(Future.successful(Some(auth)))
      (identityServiceMock.retrieve _).expects(auth.loginInfo).returns(Future.successful(None))
      (authenticatorServiceMock.remove _).expects(auth).returns(Future.successful(auth))
      await(authenticator.authenticate(auth)) should beLeft.like {
        case e => e.isInstanceOf[IdentityNotFoundException]
      }
    }

    "return a right with user when the authenticator is valid - no dao update if authenticator is untouched" >> new Context {
      val user = DummyUser("my.username")
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      val result = (auth, user)
      retrieveMock.expects(auth).returns(Future.successful(Some(auth)))
      (identityServiceMock.retrieve _).expects(auth.loginInfo).returns(Future.successful(Some(user)))
      (authenticatorServiceMock.touch _).expects(auth).returns(Left(auth))
      (authenticatorServiceMock.renew _).expects(auth).returns(Future.successful(auth))
      await(authenticator.authenticate(auth)) should beRight(result)
    }

    "return a right with user when the authenticator is valid - with dao update if authenticator is untouched" >> new Context {
      val user = DummyUser("my.username")
      val auth = DummyAuthenticator(loginInfo = LoginInfo("provider", "test@email.com"), isValid = true)
      val result = (auth, user)
      retrieveMock.expects(auth).returns(Future.successful(Some(auth)))
      (identityServiceMock.retrieve _).expects(auth.loginInfo).returns(Future.successful(Some(user)))
      (authenticatorServiceMock.touch _).expects(auth).returns(Right(auth))
      (authenticatorServiceMock.update _).expects(auth).returns(Future.successful(auth))
      (authenticatorServiceMock.renew _).expects(auth).returns(Future.successful(auth))
      await(authenticator.authenticate(auth)) should beRight(result)
    }
  }

  case class DummyAuthenticator(loginInfo: LoginInfo, isValid: Boolean) extends Authenticator {
    override type Value = this.type
    override type Settings = this.type
  }

  case class DummyUser(username: String) extends Identity

  trait Context extends MockContext {
    val authenticatorServiceMock = mock[AuthenticatorService[Authenticator]]
    val identityServiceMock = mock[IdentityService[Identity]]
    val retrieveMock = mockFunction[Authenticator, Future[Option[Authenticator]]]

    /* subjects under tests */
    val authenticator = new RorschachAuthenticator[Authenticator, Identity] {
      implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
      type Context = Authenticator

      def authenticationService = authenticatorServiceMock

      def identityService = identityServiceMock

      override def retrieve(ctx: Context): Future[Option[Authenticator]] = retrieveMock.apply(ctx)
    }

  }

}

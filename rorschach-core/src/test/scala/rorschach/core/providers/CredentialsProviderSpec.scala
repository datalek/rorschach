package rorschach.core.providers

import org.specs2.mutable.Specification
import org.scalamock.specs2.MockContext
import org.specs2.time.NoTimeConversions
import rorschach.core.{LoginInfo, Credentials}
import rorschach.core.services.AuthInfoService
import rorschach.exceptions.{InvalidPasswordException, ConfigurationException, IdentityNotFoundException}
import rorschach.util.{PasswordInfo, PasswordHasher}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import test.util.Common
import CredentialsProvider._

trait Context extends MockContext {
  val authInfoService = mock[AuthInfoService]
  val passwordHasher0 = mock[PasswordHasher]
  val passwordHasher1 = mock[PasswordHasher]

  /* common behaviour */
  (passwordHasher0.id _).expects().returns("passwordHasher0").repeat(0 to 2)
  (passwordHasher1.id _).expects().returns("passwordHasher1").repeat(0 to 2)

  /* subjects under tests */
  val provider = new CredentialsProvider(authInfoService, passwordHasher0, Seq(passwordHasher0, passwordHasher1))
}

class CredentialsProviderSpec extends Specification with NoTimeConversions with Common {

  val credentials = Credentials("identifier man", "my password is password :D")

  "the `authenticate` method" should {
    "throw IdentityNotFound when authInfo isn't found" >> new Context {
      (authInfoService.find(_: LoginInfo)(_: ClassTag[PasswordInfo])).expects(*, *).returns(Future.successful(None))
      await(provider.authenticate(credentials)) should throwA[IdentityNotFoundException].like {
        case e => e.getMessage must beEqualTo(UnknownCredentials.format(provider.id))
      }
    }

    "throw ConfigurationException when hasher isn't found" >> new Context {
      val passwordInfo = PasswordInfo("unknown", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, "other man!")
      (authInfoService.find(_: LoginInfo)(_: ClassTag[PasswordInfo])).expects(*, *).returns(Future.successful(Some(passwordInfo)))
      await(provider.authenticate(credentials)) should throwA[ConfigurationException].like {
        case e => e.getMessage must beEqualTo(UnsupportedHasher.format(provider.id, "unknown", "passwordHasher0, passwordHasher1"))
      }
    }

    "throw InvalidPasswordException if password doesn't match" >> new Context {
      val passwordInfo = PasswordInfo("passwordHasher0", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, "other man!")
      (authInfoService.find(_: LoginInfo)(_: ClassTag[PasswordInfo])).expects(*, *).returns(Future.successful(Some(passwordInfo)))
      (passwordHasher0.matches _).expects(passwordInfo, credentials.password).returns(false)

      await(provider.authenticate(credentials)) should throwA[InvalidPasswordException].like {
        case e => e.getMessage must beEqualTo(InvalidPassword.format(provider.id))
      }
    }

    "return login info if passwords does match" >> new Context {
      val passwordInfo = PasswordInfo("passwordHasher0", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      (authInfoService.find(_: LoginInfo)(_: ClassTag[PasswordInfo])).expects(*, *).returns(Future.successful(Some(passwordInfo)))
      (passwordHasher0.matches _).expects(passwordInfo, credentials.password).returns(true)

      await(provider.authenticate(credentials)) should be equalTo loginInfo
    }

    "re-hash password with new hasher" >> new Context {
      val oldPasswordInfo = PasswordInfo("passwordHasher1", "p455w0rd")
      val newPasswordInfo = PasswordInfo("passwordHasher0", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      (authInfoService.find(_: LoginInfo)(_: ClassTag[PasswordInfo])).expects(*, *).returns(Future.successful(Some(oldPasswordInfo)))
      (passwordHasher1.matches _).expects(oldPasswordInfo, credentials.password).returns(true)
      (passwordHasher0.hash _).expects(credentials.password).returns(newPasswordInfo)
      (authInfoService.update(_: LoginInfo, _: PasswordInfo)).expects(loginInfo, newPasswordInfo).returns(Future.successful(newPasswordInfo))

      await(provider.authenticate(credentials)) should be equalTo loginInfo
    }


  }

}

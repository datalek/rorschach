package rorschach.providers.credentials

import org.specs2.mutable.Specification
import org.scalamock.specs2.MockContext
import org.specs2.time.NoTimeConversions
import rorschach.core._
import rorschach.exceptions._
import rorschach.util._
import scala.concurrent.Future
import scala.reflect.ClassTag
import test.util.Common
import CredentialsProvider._
import rorschach.core.daos.AuthInfoDao

class CredentialsProviderSpec extends Specification with NoTimeConversions with Common {

  "the `authenticate` method" should {
    "throw IdentityNotFound when authInfo isn't found" >> new Context {
      (authInfoDao.find _).expects(*).returns(Future.successful(None))
      await(provider.authenticate(credentials)) should throwA[IdentityNotFoundException].like {
        case e => e.getMessage must beEqualTo(UnknownCredentials.format(provider.id))
      }
    }

    "throw ConfigurationException when hasher isn't found" >> new Context {
      val passwordInfo = PasswordInfo("unknown", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, "other man!")
      (authInfoDao.find _).expects(*).returns(Future.successful(Some(passwordInfo)))
      await(provider.authenticate(credentials)) should throwA[ConfigurationException].like {
        case e => e.getMessage must beEqualTo(UnsupportedHasher.format(provider.id, "unknown", "passwordHasher0, passwordHasher1"))
      }
    }

    "throw InvalidPasswordException if password doesn't match" >> new Context {
      val passwordInfo = PasswordInfo("passwordHasher0", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, "other man!")
      (authInfoDao.find _).expects(*).returns(Future.successful(Some(passwordInfo)))
      (passwordHasher0.matches _).expects(passwordInfo, credentials.password).returns(false)

      await(provider.authenticate(credentials)) should throwA[InvalidPasswordException].like {
        case e => e.getMessage must beEqualTo(InvalidPassword.format(provider.id))
      }
    }

    "return login info if passwords does match" >> new Context {
      val passwordInfo = PasswordInfo("passwordHasher0", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      (authInfoDao.find _).expects(*).returns(Future.successful(Some(passwordInfo)))
      (passwordHasher0.matches _).expects(passwordInfo, credentials.password).returns(true)

      await(provider.authenticate(credentials)) should be equalTo loginInfo
    }

    "re-hash password with new hasher" >> new Context {
      val oldPasswordInfo = PasswordInfo("passwordHasher1", "p455w0rd")
      val newPasswordInfo = PasswordInfo("passwordHasher0", "p455w0rd")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      (authInfoDao.find _).expects(*).returns(Future.successful(Some(oldPasswordInfo)))
      (passwordHasher1.matches _).expects(oldPasswordInfo, credentials.password).returns(true)
      (passwordHasher0.hash _).expects(credentials.password).returns(newPasswordInfo)
      (authInfoDao.update _).expects(loginInfo, newPasswordInfo).returns(Future.successful(newPasswordInfo))

      await(provider.authenticate(credentials)) should be equalTo loginInfo
    }

  }

  trait Context extends MockContext {
    // TODO: get from ExecutionContextProvider trait
    implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
    val credentials = Credentials("identifier man", "my password is password :D")

    val authInfoDao = mock[AuthInfoDao[PasswordInfo]]
    val passwordHasher0 = mock[PasswordHasher]
    val passwordHasher1 = mock[PasswordHasher]

    /* common behaviour */
    (passwordHasher0.id _).expects().returns("passwordHasher0").repeat(0 to 2)
    (passwordHasher1.id _).expects().returns("passwordHasher1").repeat(0 to 2)

    /* subjects under tests */
    val provider = new CredentialsProvider(authInfoDao, passwordHasher0, Seq(passwordHasher0, passwordHasher1))
  }

}

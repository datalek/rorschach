package rorschach.util

import org.scalamock.specs2.MockContext
import org.specs2.mutable.Specification
import rorschach.core.{LoginInfo, AuthInfo}
import rorschach.core.daos.DelegableAuthInfoDao
import test.util.Common

import scala.concurrent.Future

class DelegableAuthInfoServiceSpec extends Specification with Common {

  "the method find" should {
    "return usign the method find of the correct dao" >> new Context {
      (dao0.find _).expects(loginInfo).returns(Future.successful(Some(authInfo0)))
      (dao1.find _).expects(loginInfo).returns(Future.successful(Some(authInfo1)))
      await(authInfoService.find[AuthInfo0](loginInfo)) should be some equalTo(authInfo0)
      await(authInfoService.find[AuthInfo1](loginInfo)) should be some equalTo(authInfo1)
    }
    "throw an exception when dao isn't found" >> new Context {
      await(authInfoService.find[WithoutDao](loginInfo)) should throwA[Exception]
    }
  }

  "the method add" should {
    "return usign the method add of the correct dao" >> new Context {
      (dao0.add _).expects(loginInfo, authInfo0).returns(Future.successful(authInfo0))
      (dao1.add _).expects(loginInfo, authInfo1).returns(Future.successful(authInfo1))
      await(authInfoService.add[AuthInfo0](loginInfo, authInfo0)) should be equalTo authInfo0
      await(authInfoService.add[AuthInfo1](loginInfo, authInfo1)) should be equalTo authInfo1
    }
    "throw an exception when dao isn't found" >> new Context {
      await(authInfoService.add[WithoutDao](loginInfo, withoutDao)) should throwA[Exception]
    }
  }

  "the method update" should {
    "return usign the method update of the correct dao" >> new Context {
      (dao0.update _).expects(loginInfo, authInfo0).returns(Future.successful(authInfo0))
      (dao1.update _).expects(loginInfo, authInfo1).returns(Future.successful(authInfo1))
      await(authInfoService.update[AuthInfo0](loginInfo, authInfo0)) should be equalTo authInfo0
      await(authInfoService.update[AuthInfo1](loginInfo, authInfo1)) should be equalTo authInfo1
    }
    "throw an exception when dao isn't found" >> new Context {
      await(authInfoService.update[WithoutDao](loginInfo, withoutDao)) should throwA[Exception]
    }
  }

  "the method save" should {
    "return usign the method save of the correct dao" >> new Context {
      (dao0.save _).expects(loginInfo, authInfo0).returns(Future.successful(authInfo0))
      (dao1.save _).expects(loginInfo, authInfo1).returns(Future.successful(authInfo1))
      await(authInfoService.save[AuthInfo0](loginInfo, authInfo0)) should be equalTo authInfo0
      await(authInfoService.save[AuthInfo1](loginInfo, authInfo1)) should be equalTo authInfo1
    }
    "throw an exception when dao isn't found" >> new Context {
      await(authInfoService.save[WithoutDao](loginInfo, withoutDao)) should throwA[Exception]
    }
  }

  "the method remove" should {
    "return usign the method remove of the correct dao" >> new Context {
      (dao0.remove _).expects(loginInfo).returns(Future.successful(Some(authInfo0)))
      (dao1.remove _).expects(loginInfo).returns(Future.successful(Some(authInfo1)))
      await(authInfoService.remove[AuthInfo0](loginInfo)) should be equalTo(Unit)
      await(authInfoService.remove[AuthInfo1](loginInfo)) should be equalTo(Unit)
    }
    "throw an exception when dao isn't found" >> new Context {
      await(authInfoService.remove[WithoutDao](loginInfo)) should throwA[Exception]
    }
  }


  trait Context extends MockContext {
    val loginInfo = LoginInfo("provider", "this is identificator of user")
    val authInfo0 = AuthInfo0()
    val authInfo1 = AuthInfo1()
    val withoutDao = WithoutDao()

    case class AuthInfo0(bar: String = "bar") extends AuthInfo
    case class AuthInfo1(foo: String = "foo") extends AuthInfo
    case class WithoutDao(baz: String = "baz") extends AuthInfo
    /* generate mock */
    val dao0 = mock[DelegableAuthInfoDao[AuthInfo0]]
    val dao1 = mock[DelegableAuthInfoDao[AuthInfo1]]

    /* subjects under tests */
    val authInfoService = new DelegableAuthInfoService(dao0, dao1)

  }

}

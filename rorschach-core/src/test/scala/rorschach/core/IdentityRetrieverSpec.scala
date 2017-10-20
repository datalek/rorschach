package rorschach.core

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent._
import scala.concurrent.duration.Duration

class IdentityRetrieverSpec extends Specification {

  "the `instance` method" should {
    "create a new IdentityRetriever with the given function" in new Context {
      val value = 1
      val expected = Await.result(funMock(value), Duration.Inf)
      val res = IdentityRetriever.instance(funMock)(value)
      Await.result(res, Duration.Inf) must be equalTo expected
    }
  }

  trait Context extends Scope {
    val funMock = (i: Int) => Future.successful(Option(i.toString))
  }

}

package rorschach.util

import org.specs2.mutable.Specification
import rorschach.util._
import scala.concurrent.Future

class DefaultGeneratorSpec extends Specification {

  val generator = DefaultGenerator

  "DefaultGenerator" should {
    "generate different values without error" >> {
      val ids = (0 until 1000).map(i => generator.generate)
      val doubles = Future.sequence(ids).map(_.groupBy(identity).filter { case (v, l) => l.length > 1 })
      doubles.map(_ should be empty).await
    }
  }

}
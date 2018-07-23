package rorschach.util

import java.time.Instant
import org.specs2.mutable.Specification

class ClockSpec extends Specification {

  "The `now` method" should {
    "return a new DateTime instance" in {
      Clock.now should beAnInstanceOf[Instant]
    }
  }

}
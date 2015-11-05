package rorschach.util

import org.joda.time.DateTime
import org.specs2.mutable.Specification

class ClockSpec extends Specification {

  "The `now` method" should {
    "return a new DateTime instance" in {
      Clock.now should beAnInstanceOf[DateTime]
    }
  }

}
package rorschach.util

import java.time.Instant

/**
 * The purpose of this class is only to mock date system
 */
trait Clock {
  def now = Instant.now
}

object Clock extends Clock

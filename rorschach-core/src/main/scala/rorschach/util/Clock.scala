package rorschach.util

import org.joda.time.DateTime

/**
 * The purpose of this class is only to mock date system
 */
trait Clock {
  def now = DateTime.now
}

object Clock extends Clock

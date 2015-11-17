package rorschach.util

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._

import scala.concurrent.duration._

/**
 * Some implicit Json formats.
 */
object JsonFormats {

  /**
   * Converts [[scala.concurrent.duration.FiniteDuration]] object to JSON and vice versa.
   */
  implicit object FiniteDurationFormat extends Format[FiniteDuration] {
    def reads(json: JsValue): JsResult[FiniteDuration] = LongReads.reads(json).map(_.seconds)
    def writes(o: FiniteDuration): JsValue = LongWrites.writes(o.toSeconds)
  }
}


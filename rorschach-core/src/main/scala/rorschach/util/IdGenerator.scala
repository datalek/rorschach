package rorschach.util

import scala.concurrent._

/**
 *
 */
trait IdGenerator {

  def generate: Future[String]

}

object DefaultGenerator extends IdGenerator {
  def generate: Future[String] = Future.successful(java.util.UUID.randomUUID.toString)
}

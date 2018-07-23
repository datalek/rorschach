package rorschach.core.extractors

import scala.concurrent.Future

/**
 * Extract an authenticator from Request
 *
 * @tparam R The type of request
 * @tparam A The type of authenticator
 */
trait AuthenticatorExtractor[-R, +A] extends (R => Future[Option[A]])

object AuthenticatorExtractor {

  /* materializer/constructor method */
  def instance[R, A](f: R => Future[Option[A]]): AuthenticatorExtractor[R, A] = new AuthenticatorExtractor[R, A] {
    override def apply(v1: R) = f(v1)
  }

}
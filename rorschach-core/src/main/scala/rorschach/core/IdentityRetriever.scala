package rorschach.core

import scala.concurrent.Future

/**
 * Retrieve an identity given an authenticator
 *
 * @tparam A The type of authenticator
 * @tparam I The type of identity
 */
trait IdentityRetriever[-A, +I] extends (A => Future[Option[I]])

object IdentityRetriever {

  /* materializer/constructor method */
  def instance[A, I](f: A => Future[Option[I]]): IdentityRetriever[A, I] = new IdentityRetriever[A, I] {
    override def apply(v1: A) = f(v1)
  }

}
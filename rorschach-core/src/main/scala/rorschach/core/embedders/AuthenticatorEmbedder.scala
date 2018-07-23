package rorschach.core.embedders

/**
 * Embed the authenticator to the output
 * @tparam A The type of authenticator
 * @tparam O The type of output (response)
 */
trait AuthenticatorEmbedder[-A, +O] extends (A => O)

object AuthenticatorEmbedder {

  /* materializer/constructor method */
  def instance[A, O](f: A => O): AuthenticatorEmbedder[A, O] = new AuthenticatorEmbedder[A, O] {
    override def apply(v1: A) = f(v1)
  }

}
package rorschach.util

import BCryptPasswordHasher._
import org.mindrot.jbcrypt.BCrypt

/**
 * Implementation of the password hasher based on BCrypt.
 *
 * @param logRounds The log2 of the number of rounds of hashing to apply.
 * @see [[http://www.mindrot.org/files/jBCrypt/jBCrypt-0.2-doc/BCrypt.html#gensalt(int) gensalt]]
 */
class BCryptPasswordHasher(logRounds: Int = 10) extends PasswordHasher {

  /**
   * Gets the ID of the hasher.
   *
   * @return The ID of the hasher.
   */
  override def id = ID

  /**
   * Hashes a password.
   *
   * This implementation does not return the salt separately because it is embedded in the hashed password.
   * Other implementations might need to return it so it gets saved in the backing store.
   *
   * @param plainPassword The password to hash.
   * @return A PasswordInfo containing the hashed password.
   */
  override def hash(plainPassword: String) = PasswordInfo(id, BCrypt.hashpw(plainPassword, BCrypt.gensalt(logRounds)))

  /**
   * Checks if a password matches the hashed version.
   *
   * @param passwordInfo The password retrieved from the backing store.
   * @param suppliedPassword The password supplied by the user trying to log in.
   * @return True if the password matches, false otherwise.
   */
  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String) = {
    BCrypt.checkpw(suppliedPassword, passwordInfo.password)
  }
}

/**
 * The companion object.
 */
object BCryptPasswordHasher {

  /**
   * The ID of the hasher.
   */
  val ID = "bcrypt"
}


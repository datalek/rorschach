package rorschach.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

object Crypto {

  protected val algorithm = "AES"
  protected val transformation = "AES/CTR/NoPadding"
  // TODO: Add this to configuration
  private val SALT: String = "jMhKlOuJnM34G6NHkqo9V010GhLAqOpF0BePojHgh1HgNg8^72k"

  private def secretKeyToSpec(key: String): SecretKeySpec = {
    val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")
    val keyBytes: Array[Byte] = messageDigest.digest((SALT + key).getBytes("UTF-8"))
    // max allowed length in bits / (8 bits = 1 byte)
    val maxAllowedKeyLength = Cipher.getMaxAllowedKeyLength(algorithm) / 8
    val raw = messageDigest.digest().slice(0, maxAllowedKeyLength)
    new SecretKeySpec(raw, algorithm)
  }

  def encrypt(key: String, value: String): String = {
    val skeySpec = secretKeyToSpec(key)
    val cipher = Cipher.getInstance(transformation)
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
    val encryptedValue = cipher.doFinal(value.getBytes("UTF-8"))
    // return a formatted, versioned encrypted string encrypted payload with an IV
    Option(cipher.getIV) match {
      case Some(iv) => Base64.encode(iv ++ encryptedValue)
      case None => throw new Exception("Error on Crypto, not IV found")
    }
  }

  def decrypt(key: String, value: String): String = {
    val data = Base64.decodeAsByteArray(value)
    val skeySpec = secretKeyToSpec(key)
    val cipher = Cipher.getInstance(transformation)
    val blockSize = cipher.getBlockSize
    val iv = data.slice(0, blockSize)
    val payload = data.slice(blockSize, data.length)
    cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(iv))
    new String(cipher.doFinal(payload), "utf-8")
  }

}

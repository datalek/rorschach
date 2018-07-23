package rorschach.util

import java.util.{ Base64 => JBase64 }
import java.nio.charset.StandardCharsets

/**
 * Base64 helper.
 */
object Base64 {

  /**
   * Decodes a Base64 string.
   *
   * @param str The string to decode.
   * @return The decoded string.
   */
  def decode(str: String): String = new String(this.decodeAsByteArray(str), StandardCharsets.UTF_8)

  def decodeAsByteArray(str: String): Array[Byte] = JBase64.getDecoder.decode(str.getBytes(StandardCharsets.UTF_8))

  /**
   * Encodes a string as Base64.
   *
   * @param str The string to encode.
   * @return The encodes string.
   */
  def encode(str: String): String = this.encode(str.getBytes(StandardCharsets.UTF_8))

  def encode(binaryData: Array[Byte]): String = new String(JBase64.getEncoder.encode(binaryData), StandardCharsets.UTF_8)

}
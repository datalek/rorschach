package rorschach.util

//import java.test.util.{Base64 => JBase64}

import java.nio.charset.StandardCharsets

import org.apache.commons.codec.binary.{ Base64 => JBase64 }
import play.api.libs.json.JsValue

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

  def decodeAsByteArray(str: String): Array[Byte] = JBase64.decodeBase64(str.getBytes(StandardCharsets.UTF_8))

  /**
   * Encodes a string as Base64.
   *
   * @param str The string to encode.
   * @return The encodes string.
   */
  def encode(str: String): String = this.encode(str.getBytes(StandardCharsets.UTF_8))

  def encode(binaryData: Array[Byte]): String = new String(JBase64.encodeBase64(binaryData), StandardCharsets.UTF_8)

  /**
   * Encodes a Json value as Base64.
   *
   * @param json The json value to encode.
   * @return The encoded value.
   */
  def encode(json: JsValue): String = encode(json.toString())
}
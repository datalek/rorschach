package rorschach.util

import org.specs2.mutable.Specification
import play.api.libs.json.Json

class Base64Spec extends Specification {

  val textToEncode = "this is the string to encode?!"
  val textEncoded = "dGhpcyBpcyB0aGUgc3RyaW5nIHRvIGVuY29kZT8h"
  val jsonToEncode = Json.obj("test" -> "man", "key" -> Json.obj("other" -> "value"))

  val base64 = Base64

  "Base64" should {
    "encode a string without error" >> {
      val encoded = base64.encode(textToEncode)
      encoded must be equalTo textEncoded
    }
    "decode a string without error" >> {
      val decoded = base64.decode(textEncoded)
      decoded must be equalTo textToEncode
    }
    "encode and decode a json without error" >> {
      val encoded = base64.encode(jsonToEncode)
      val decoded = base64.decode(encoded)
      Json.parse(decoded) should be equalTo jsonToEncode
    }
  }

}

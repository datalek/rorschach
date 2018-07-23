package test.util

import scala.concurrent.{ Await, Awaitable }
import scala.concurrent.duration._

trait Common {

  def await[T](awaitable: Awaitable[T]) = Await.result(awaitable, 5.seconds)

}

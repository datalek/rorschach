import sbt._

object Dependencies {
  val jbcrypt = "org.mindrot" % "jbcrypt" % "0.3m"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
  val playJson = "com.typesafe.play" %% "play-json" % "2.5.5"
  val scalamockScalatest = "org.scalamock" %% "scalamock-scalatest-support" % "3.2"
  val scalamockSpec2 = "org.scalamock" %% "scalamock-specs2-support" % "3.2"

  object jwt {
    val version = "1.2.4"
    val core = "com.atlassian.jwt" % "jwt-core" % version
    val api = "com.atlassian.jwt" % "jwt-api" % version
  }

  object spray {
    val version = "1.3.3"
    val routing = "io.spray" %% "spray-routing" % version
    val testkit = "io.spray" %% "spray-testkit" % version
  }
  object akka {
    val version = "2.4.9"
    val http = "com.typesafe.akka" %% "akka-http-experimental" % version
    val httpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % version
  }
}
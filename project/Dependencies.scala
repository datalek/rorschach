import sbt._

object Dependencies {
  val commonResolvers = Seq(
    Resolver.defaultLocal, Resolver.mavenLocal,
    "Atlassian Releases" at "https://maven.atlassian.com/public/",
    "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
  )
  
  val jbcrypt = "org.mindrot" % "jbcrypt" % "0.4"
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val playJson = "com.typesafe.play" %% "play-json" % "2.6.9"
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.1.0"

  object Specs2 {
    val version = "3.8.6"
    val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
  }

  object Scalamock {
    val version = "3.6.0"
    val scalatest = "org.scalamock" %% "scalamock-scalatest-support" % version
    val spec2 = "org.scalamock" %% "scalamock-specs2-support" % version
  }

  object jwt {
    val version = "2.0.2"
    val core = "com.atlassian.jwt" % "jwt-core" % version
    val api = "com.atlassian.jwt" % "jwt-api" % version
  }

  object akka {
    val version = "2.5.12"
    val httpVersion = "10.1.3"
    val stream = "com.typesafe.akka" %% "akka-stream" % version
    val http = "com.typesafe.akka" %% "akka-http" % httpVersion
    val httpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % httpVersion
  }
}
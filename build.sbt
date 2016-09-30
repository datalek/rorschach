import Dependencies._

lazy val sprayRorschachSettings = Seq(
  organization := "com.github.datalek",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.11.6",
  crossPaths := true,
  resolvers += Resolver.jcenterRepo
) ++ PublishSettings.publish

val sprayVersion = "1.3.3"
val akkaVersion = "2.4.9"
val playJsonVersion = "2.5.5"

/* the root project, contains startup stuff */
lazy val root = (project in file("."))
  .settings(sprayRorschachSettings: _*)
  .settings(PublishSettings.skipPublish)
  .aggregate(rorschachCore, rorschachSpray, rorschachAkka)

/* core project, contains main behaviour */
lazy val rorschachCore = Project(id = "rorschach-core", base = file("rorschach-core"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/",
    resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
    libraryDependencies ++= Seq(jwt.core, jwt.api, jbcrypt, logback, playJson, scalamockSpec2 % "test")
  )

/* this project provide authentication functionality into spray */
lazy val rorschachSpray = Project(id = "rorschach-spray", base = file("rorschach-spray"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(spray.routing % "provided", spray.testkit % "test", scalamockSpec2 % "test")
  ).dependsOn(rorschachCore)

/* this project provide authentication functionality into akka-http */
lazy val rorschachAkka = Project(id = "rorschach-akka", base = file("rorschach-akka"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(akka.http % "provided", akka.httpTestkit % "test", scalamockScalatest % "test")
  ).dependsOn(rorschachCore)
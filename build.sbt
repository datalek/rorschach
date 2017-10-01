import Dependencies._

lazy val sprayRorschachSettings = Seq(
  organization := "com.github.datalek",
  version := "1.0.0-SNAPSHOT",
  scalaVersion := "2.11.6",
  crossPaths := true,
  resolvers ++= Dependencies.commonResolvers
) ++ PublishSettings.publish

val projectName = "rorschach"

/* the root project, contains startup stuff */
lazy val root = Project(id = projectName, base = file("."))
  .settings(sprayRorschachSettings: _*)
  .settings(PublishSettings.skipPublish)
  .aggregate(rorschachCore, rorschachJwt, rorschachProviders, /*rorschachSpray, */rorschachAkka)

/* core project, contains main behaviour */
lazy val rorschachCore = Project(id = s"$projectName-core", base = file(s"$projectName-core"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(jwt.core, jwt.api, jbcrypt, logback, playJson, scalamockSpec2 % "test")
  )

/* implements jwt authenticator */
lazy val rorschachJwt = Project(id = s"$projectName-jwt", base = file(s"$projectName-jwt"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(jwt.core, jwt.api, jbcrypt, logback, playJson, scalamockSpec2 % "test")
  ).dependsOn(rorschachCore)

/* implements providers (oauth, openId, saml, password, ...) */
lazy val rorschachProviders = Project(id = s"$projectName-providers", base = file(s"$projectName-providers"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(scalamockSpec2 % "test")
  ).dependsOn(rorschachCore)

/* this project provide authentication functionality into spray */
lazy val rorschachSpray = Project(id = s"$projectName-spray", base = file(s"$projectName-spray"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(spray.routing % "provided", spray.testkit % "test", scalamockSpec2 % "test")
  ).dependsOn(rorschachCore)

/* this project provide authentication functionality into akka-http */
lazy val rorschachAkka = Project(id = s"$projectName-akka", base = file(s"$projectName-akka"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(akka.http % "provided", akka.httpTestkit % "test", scalamockScalatest % "test")
  ).dependsOn(rorschachCore)
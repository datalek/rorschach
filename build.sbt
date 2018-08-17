import Dependencies._

lazy val sprayRorschachSettings = Seq(
  organization := "com.github.datalek",
  version := "1.0.0-SNAPSHOT",
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  crossPaths := true,
  resolvers ++= Dependencies.commonResolvers
) ++ ProjectSettings.common ++ ProjectSettings.publish

val projectName = "rorschach"

/* the root project, contains startup stuff */
lazy val root = Project(id = projectName, base = file("."))
  .settings(sprayRorschachSettings: _*)
  .settings(ProjectSettings.skipPublish)
  .aggregate(rorschachCore, rorschachJwt, rorschachProviders, /*rorschachSpray, */rorschachAkka)

/* core project, contains main behaviour */
lazy val rorschachCore = Project(id = s"$projectName-core", base = file(s"$projectName-core"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(logback, Scalamock.spec2 % Test)
  )

/* implements jwt authenticator */
lazy val rorschachJwt = Project(id = s"$projectName-jwt", base = file(s"$projectName-jwt"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(jwt.core, jwt.api, logback, playJson, Scalamock.spec2 % Test, Specs2.matcherExtra % Test)
  ).dependsOn(rorschachCore)

/* implements providers (oauth, openId, saml, password, ...) */
lazy val rorschachProviders = Project(id = s"$projectName-providers", base = file(s"$projectName-providers"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(jbcrypt, Scalamock.spec2 % Test)
  ).dependsOn(rorschachCore)

/* this project provide authentication functionality into akka-http */
lazy val rorschachAkka = Project(id = s"$projectName-akka", base = file(s"$projectName-akka"))
  .settings(sprayRorschachSettings: _*)
  .settings(
    libraryDependencies ++= Seq(akka.http, akka.stream, scalaXml % Test, akka.httpTestkit % Test, Scalamock.scalatest % Test)
  ).dependsOn(rorschachCore)
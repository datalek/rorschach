import sbt.Keys._
import sbt._

object PublishSettings {

  private val pom = {
    <scm>
      <url>https://github.com/datalek/rorschach.git</url>
      <connection>scm:git:git@github.com:datalek/rorschach.git</connection>
    </scm>
    <developers>
      <developer>
        <id>datalek</id>
        <name>Alessandro Ferlin</name>
        <url>https://github.com/datalek</url>
      </developer>
    </developers>
  }

  private val snapshot = Seq(
    publishTo := {if(isSnapshot.value) Some("snapshots" at "http://oss.jfrog.org/artifactory/oss-snapshot-local") else None},
    //bintray.BintrayKeys.bintrayReleaseOnPublish := {if(isSnapshot.value) false else true},
    // Only setting the credentials file if it exists (#52)
    credentials := {if (isSnapshot.value) List(Path.userHome / ".bintray" / ".artifactory").filter(_.exists).map(Credentials(_)) else Nil}
  )
  val publish = snapshot ++ bintray.BintrayPlugin.bintrayPublishSettings ++ Seq(
    bintray.BintrayKeys.bintrayPackage := "rorschach",
    pomExtra := pom,
    publishArtifact in Test := false,
    homepage := Some(url("https://github.com/datalek/rorschach")),
    resolvers += Resolver.url("supler ivy resolver", url("http://dl.bintray.com/merle/maven"))(Resolver.ivyStylePatterns),
    licenses := ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil // this is required! otherwise Bintray will reject the code
  )
}
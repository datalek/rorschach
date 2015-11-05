
lazy val sprayRorschachSettings = Seq(
	organization := "merle",
	version := "0.0.1",
	scalaVersion := "2.11.6",
	crossPaths := false
)

val sprayVersion = "1.3.3"

/* the root project, contains startup stuff */
lazy val root = (project in file("."))
	.settings(sprayRorschachSettings: _*)
	.aggregate(rorschachCore, rorschachSpray)
	.dependsOn(rorschachCore, rorschachSpray)

/* Core project, contain main behaviour */
lazy val rorschachCore = Project(id = "rorschach-core", base = file("rorschach-core"))
	.settings(sprayRorschachSettings: _*)
	.settings(
		resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/",
		resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
		libraryDependencies ++= Seq(
			"com.atlassian.jwt" 	%  "jwt-core" 								% "1.2.4",
			"com.atlassian.jwt" 	%  "jwt-api" 									% "1.2.4",
			"org.mindrot" 				%  "jbcrypt" 									% "0.3m",
		  "ch.qos.logback"  		%  "logback-classic" 					% "1.1.3",
		  "com.typesafe.play" 	%% "play-json" 								% "2.3.9",
//		  "org.specs2" 					%% "specs2-core" 							% "2.3.11" 	% "test",
			"org.scalamock" 			%% "scalamock-specs2-support" % "3.2" 		% "test"
		)
	)

/* this project provide authentication functionality into spray */
lazy val rorschachSpray = Project(id = "rorschach-spray", base = file("rorschach-spray"))
	.settings(sprayRorschachSettings: _*)
	.settings(
		resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/",
		resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
		libraryDependencies ++= Seq(
		    "io.spray" 							%%  "spray-routing" 	% sprayVersion,
		    "io.spray"            	%%  "spray-testkit" 	% sprayVersion 	% "test"
		)
	).dependsOn(rorschachCore)
import xerial.sbt.Sonatype._
import sbt.sbtpgp.Compat._
name := "pekko-quartz-scheduler"

organization := "io.github.samueleresca"

version := "1.2.0-pekko-1.0.x"

val Scala212Version = "2.12.18"
val Scala213Version = "2.13.12"
val Scala3Version = "3.3.1"
val PekkoVersion = "1.0.1"

ThisBuild / scalaVersion := Scala213Version
ThisBuild / crossScalaVersions := Seq(Scala212Version, Scala213Version, Scala3Version)
ThisBuild / scalacOptions ++= Seq("-language:postfixOps")

libraryDependencies ++= Seq(
  "org.apache.pekko"   %% "pekko-actor"       % PekkoVersion,
  "org.apache.pekko"   %% "pekko-actor-typed" % PekkoVersion,
  "org.quartz-scheduler" % "quartz"           % "2.3.2"
    exclude ("com.zaxxer", "HikariCP-java7"),
  "org.apache.pekko" %% "pekko-testkit"             % PekkoVersion % Test,
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion % Test,
  "org.specs2"        %% "specs2-core"              % "4.20.3" % Test,
  "org.specs2"        %% "specs2-junit"             % "4.20.3" % Test,
  "junit"              % "junit"                    % "4.13.2"   % Test,
  "org.slf4j"          % "slf4j-api"                % "2.0.9" % Test,
  "org.slf4j"          % "slf4j-jcl"                % "1.7.36" % Test,
  "org.scalatest"     %% "scalatest"                % "3.2.17" % Test
)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

// Sonatype release settings
pomIncludeRepository := { _ => false }
sonatypeCredentialHost := "s01.oss.sonatype.org"
publishTo := sonatypePublishToBundle.value
sonatypeProjectHosting := Some(
  GitHubHosting(user = "samueleresca", repository = "pekko-quartz-scheduler", email = "samuele.resca@gmail.com"))
  // Metadata referrsing to licenses, website, and SCM (source code management)
licenses:= Seq(
  "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
sonatypeProfileName := "io.github.samueleresca"
publishMavenStyle := true
scmInfo := Some(
  ScmInfo(
    url("https://github.com/samueleresca/pekko-quartz-scheduler"),
    "scm:git@github.com:samueleresca/pekko-quartz-scheduler.git"))

name := "pekko-quartz-scheduler"

organization := "com.github.samueleresca"

version := "0.0.1-pekko-1.0.x"

val Scala212Version = "2.12.13"
val Scala213Version = "2.13.8"
val Scala3Version = "3.1.3"
val PekkoVersion = "1.0.1"

ThisBuild / crossScalaVersions := Seq(Scala212Version, Scala213Version, Scala3Version)
ThisBuild / scalacOptions ++= Seq("-language:postfixOps")

libraryDependencies ++= Seq(
  "org.apache.pekko"   %% "pekko-actor"       % PekkoVersion,
  "org.apache.pekko"   %% "pekko-actor-typed" % PekkoVersion,
  "org.quartz-scheduler" % "quartz"           % "2.3.2"
    exclude ("com.zaxxer", "HikariCP-java7"),
  "org.apache.pekko" %% "pekko-testkit"             % PekkoVersion % Test,
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion % Test,
  "org.specs2"        %% "specs2-core"              % "4.15.0" % Test,
  "org.specs2"        %% "specs2-junit"             % "4.15.0" % Test,
  "junit"              % "junit"                    % "4.12"   % Test,
  "org.slf4j"          % "slf4j-api"                % "1.7.21" % Test,
  "org.slf4j"          % "slf4j-jcl"                % "1.7.21" % Test,
  "org.scalatest"     %% "scalatest"                % "3.2.12" % Test
)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

pomIncludeRepository := { _ => false }

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

//useGpg := true

pomExtra := <url>https://github.com/samueleresca/pekko-quartz-scheduler</url>
    <licenses>
        <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
        </license>
    </licenses>
    <scm>
        <url>https://github.com/samueleresca/pekko-quartz-scheduler.git</url>
        <connection>https://github.com/samueleresca/pekko-quartz-scheduler.git</connection>
    </scm>
    <developers>
        <developer>
            <name>Samuele Resca</name>
            <email>samuele.resca@gmail.com</email>
        </developer>
    </developers>

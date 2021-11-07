name := "akka-quartz-scheduler"

organization := "com.enragedginger"

version := "1.9.2-akka-2.6.x"

ThisBuild / scalaVersion := "2.13.5"

crossScalaVersions := Seq("2.12.13", "2.13.5")

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-actor"       % "2.6.10" % "provided",
  "com.typesafe.akka"   %% "akka-actor-typed" % "2.6.10" % "provided",
  "org.quartz-scheduler" % "quartz"           % "2.3.2"
    exclude ("com.zaxxer", "HikariCP-java7"),
  "com.typesafe.akka" %% "akka-testkit"             % "2.6.10" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.10" % Test,
  "org.specs2"        %% "specs2-core"              % "4.5.1"  % Test,
  "org.specs2"        %% "specs2-junit"             % "4.5.1"  % Test,
  "junit"              % "junit"                    % "4.12"   % Test,
  "org.slf4j"          % "slf4j-api"                % "1.7.21" % Test,
  "org.slf4j"          % "slf4j-jcl"                % "1.7.21" % Test,
  "org.scalatest"     %% "scalatest"                % "3.2.6"  % Test
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

pomExtra := <url>https://github.com/enragedginger/akka-quartz-scheduler</url>
    <licenses>
        <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
        </license>
    </licenses>
    <scm>
        <url>https://github.com/enragedginger/akka-quartz-scheduler.git</url>
        <connection>https://github.com/enragedginger/akka-quartz-scheduler.git</connection>
    </scm>
    <developers>
        <developer>
            <name>Stephen M. Hopper</name>
            <email>stephen@enragedginger.com</email>
        </developer>
    </developers>

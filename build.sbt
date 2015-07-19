name := "akka-quartz-scheduler"

organization := "com.enragedginger"

version := "1.4.0-akka-2.3.x"

scalaVersion := "2.11.6"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"


libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.12" % "provided",
    "org.quartz-scheduler" % "quartz" % "2.2.1",
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % "2.3.12" % "test",
    "org.specs2" %% "specs2" % "2.3.12" % "test",
    "junit" % "junit" % "4.7" % "test",
    "org.slf4j" % "slf4j-api" % "1.6.1" % "test",
    "org.slf4j" % "slf4j-jcl" % "1.6.1" % "test",
    "org.scalatest" %% "scalatest" % "2.1.6" % "test"
)

scalacOptions ++= Seq("-deprecation")

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/enragedginger/akka-quartz-scheduler</url>
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
    </developers>)

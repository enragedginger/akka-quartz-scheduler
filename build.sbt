name := "akka-quartz-scheduler"

organization := "com.typesafe.akka"

version := "1.2.0-akka-2.3.x"

scalaVersion := "2.11.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"


libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.4" % "provided",
    "org.quartz-scheduler" % "quartz" % "2.2.1",
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % "2.3.4" % "test",
    "org.specs2" %% "specs2" % "2.3.12" % "test",
    "junit" % "junit" % "4.7" % "test",
    "org.slf4j" % "slf4j-api" % "1.6.1" % "test",
    "org.slf4j" % "slf4j-jcl" % "1.6.1" % "test",
    "org.scalatest" %% "scalatest" % "2.1.6" % "test"
)

scalacOptions ++= Seq("-deprecation")

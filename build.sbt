name := "akka-quartz-scheduler" 

organization := "com.typesafe.akka"

version := "1.2.0-akka-2.2.x"

scalaVersion := "2.10.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"


libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.2.3" % "provided",
    "org.quartz-scheduler" % "quartz" % "2.1.7",
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test",
    "org.specs2" %% "specs2" % "1.12.3" % "test",
    "junit" % "junit" % "4.7" % "test",
    "org.slf4j" % "slf4j-api" % "1.6.1" % "test",
    "org.slf4j" % "slf4j-jcl" % "1.6.1" % "test",
    "org.scalatest" %% "scalatest" % "1.9.1" % "test"
)

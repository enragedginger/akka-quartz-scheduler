name := "Akka Quartz Scheduler Extension"

organization := "com.typesafe.akka"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= Seq(
    "com.typesafe.akka" % "akka-actor" % "2.0.5",
    "org.quartz-scheduler" % "quartz" % "2.1.7",
    "org.specs2" %% "specs2" % "1.12.3" % "test",
    "junit" % "junit" % "4.7" % "test"
)

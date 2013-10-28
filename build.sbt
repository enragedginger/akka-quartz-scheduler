name := "akka-quartz-scheduler_akka-2.0.x"

organization := "com.typesafe.akka"

version := "1.1.0"

scalaVersion := "2.9.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"

publishTo := Some(Resolver.url("maven-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))

publishMavenStyle := true


libraryDependencies ++= Seq(
    "com.typesafe.akka" % "akka-actor" % "2.0.5" % "provided",
    "org.quartz-scheduler" % "quartz" % "2.1.7",
    // test dependencies
    "com.typesafe.akka" % "akka-testkit" % "2.0.5" % "test",
    "org.specs2" %% "specs2" % "1.12.3" % "test",
    "junit" % "junit" % "4.7" % "test",
    "org.slf4j" % "slf4j-api" % "1.6.1" % "test",
    "org.slf4j" % "slf4j-jcl" % "1.6.1" % "test",
    "org.scalatest" %% "scalatest" % "1.6.1" % "test"
)

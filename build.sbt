name := "akka-quartz-scheduler_akka-2.0.x"

organization := "com.typesafe.akka"

version := "1.0.0"

scalaVersion := "2.9.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"

publishTo := Some("Typesafe Nexus Repository" at "https://repo.typesafe.com/typesafe/releases")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials") 

libraryDependencies ++= Seq(
    "com.typesafe.akka" % "akka-actor" % "2.0.5" % "provided",
    "org.quartz-scheduler" % "quartz" % "2.1.7",
    // test dependencies
    "com.typesafe.akka" % "akka-testkit" % "2.0.5" % "test",
    "org.specs2" %% "specs2" % "1.12.3" % "test",
    "junit" % "junit" % "4.7" % "test"
)

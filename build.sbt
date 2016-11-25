name := "akka-quartz-scheduler"

organization := "com.enragedginger"

version := "1.6.0-akka-2.4.x"

scalaVersion in ThisBuild := "2.12.0"

crossScalaVersions := Seq("2.11.8", "2.12.0")

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.4.14" % "provided" ,
    "org.quartz-scheduler" % "quartz" % "2.2.3",
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % "2.4.14" % "test",
    "org.specs2" %% "specs2-core" % "3.8.6" % "test",
    "org.specs2" %% "specs2-junit" % "3.8.6" % "test",
    "junit" % "junit" % "4.12" % "test",
    "org.slf4j" % "slf4j-api" % "1.7.21" % "test",
    "org.slf4j" % "slf4j-jcl" % "1.7.21" % "test",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

resolvers ++= Seq(
  "Local Maven Repository"       at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
  
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

name := "akka-quartz-scheduler"

organization := "com.enragedginger"

version := "1.8.3-akka-2.6.x"

scalaVersion in ThisBuild := "2.13.1"

crossScalaVersions := Seq("2.11.8", "2.12.8", "2.13.1")

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.6.0" % "provided",
    "org.quartz-scheduler" % "quartz" % "2.3.2" exclude ("com.zaxxer", "HikariCP-java7"),
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % "2.6.0" % "test",
    "org.specs2" %% "specs2-core" % "4.5.1" % "test",
    "org.specs2" %% "specs2-junit" % "4.5.1" % "test",
    "junit" % "junit" % "4.12" % "test",
    "org.slf4j" % "slf4j-api" % "1.7.21" % "test",
    "org.slf4j" % "slf4j-jcl" % "1.7.21" % "test",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test"
)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  
pomIncludeRepository := { _ => false }

publishMavenStyle := true

publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
    else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

//useGpg := true

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

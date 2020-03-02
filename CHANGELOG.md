## Changelog ##

Every good library has a changelog. Somehow, we didn't have one yet.

## Version 1.8.3 ##

* Marked HikariCP as an excluded dependency due to classpath conflicts with other projects such as Play!. Issue [#83](https://github.com/enragedginger/akka-quartz-scheduler/issues/83).

## Version 1.8.2 ##

* Added Akka 2.6.0 support by fixing issue with ExtensionKey usage.

## Version 1.8.1 ##

* Added Scala 2.13.0 support.

## Version 1.8.0 ##

* Previous version: 1.7.1
* Added functionality for creating / deleting / managing job schedules. Issue [#79](https://github.com/enragedginger/akka-quartz-scheduler/issues/79), PR [#81](https://github.com/enragedginger/akka-quartz-scheduler/pull/81).

## Version 1.7.1 ##

### Changes ###

* Previous version: 1.7.0
* Fixed an issue with Java compatibility by simply changing from Scala 2.12.4 to Scala 2.12.7. Nothing changed for the 2.11 build.

### Releases ###

* `libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.7.1-akka-2.5.x"`
* `libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.7.1-akka-2.4.x"`

## Version 1.7.0 ##

### Changes ###

* Previous version: 1.6.1
* Schedule names are now properly case sensitive. Good times.

### Releases ###

* `libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.7.0-akka-2.5.x"`
* `libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.7.0-akka-2.4.x"`
## Changelog ##

Every good library has a changelog.

## Version 1.9.0 ##
* Added [Akka Typed Actor](https://doc.akka.io/docs/akka/2.5.32/typed/) compatibility. PR [#102](https://github.com/enragedginger/akka-quartz-scheduler/pull/102)
* Upgrade Akka Actor to 2.6.10 version.
* Upgrade Akka Test Kit to 2.6.10 version.
* Extract Functional Test Sample Configuration to it's own class.

## Version 1.8.5 ##
* Added previous and next firing times to messages where applicable. PR [#80](https://github.com/enragedginger/akka-quartz-scheduler/pull/80)
* Updated config from val to def to allow it to be overriden. Pr [#100](https://github.com/enragedginger/akka-quartz-scheduler/pull/100)
* Upgraded the project to SBT 1.3.13 and removed some unnecessary plugins for the project.
* Some of the tests use America/San_Francisco as a timezone. This timezone doesn't exist, so some time was spent "fixing" this but ultimately the changes
were mostly reverted. Again, this should only affect the tests and shouldn't affect the library itself.
* Updated to Scala 2.12.12 and 2.13.3

## Version 1.8.4 ##

* Officially removed support for Scala 2.11 since Akka 2.6+ requires Scala 2.12+. PR [#97](https://github.com/enragedginger/akka-quartz-scheduler/pull/97)
* Fixed issue with concurrency concerns. Issue [#96](https://github.com/enragedginger/akka-quartz-scheduler/pull/96) and [#98](https://github.com/enragedginger/akka-quartz-scheduler/pull/98)

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
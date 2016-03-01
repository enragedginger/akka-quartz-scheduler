akka-quartz-scheduler
=====================

Quartz Extension and utilities for true scheduling in Akka 2.3.x.

Current release is built for Scala 2.11.x and Akka 2.3.x and is available on Maven Central.  If you would like support
for a different combination of Scala and Akka, simply post your request on the issues page (as well as a reason as to
why the currently available versions won't work for you.  I'm always curious about these things).

# Why Akka and Quartz?

Note that this is named and targeted as akka-quartz-scheduler for a reason: it is *not* a complete port of Quartz.
Rather, we utilize the concepts of Quartz' scheduling system to provide a more robust and reliable scheduling component
to Akka than the one already available.

The goal here is to provide Akka with a scheduling system that is closer to what one would expect for Cron type jobs –
set up long-running ActorSystems that can have certain events kicked off by Quartz.

There aren't currently any plans on having anything to do with the distributed transaction, persistence,
clustering or any other nonsense anytime soon. This is for cron-ey type timing and scheduling.

There is the ability in Quartz to pass JobDataMaps around that accrue mutable state across job ticks;
we currently do NOT support that to enforce integrity, but may expose a deeper actor structure later that
gives some ability to work around that, if need arises.

## Why Not Use $OtherComparableTool Instead?

1. What's wrong with Akka's existing Scheduler?
    As Viktor Klang points out, 'Perhaps the name "Scheduler" was unfortunate,
    "Deferer" is probably more indicative of what it does.'

    The Akka Scheduler is designed to setup events that happen based on durations from the current moment:
    You can say "fire this job in 15 minutes, every 30 minutes thereafter" but not "fire a job every day at 3pm".

    Furthermore, Akka's default scheduler is executed around a [`HashedWheelTimer`](http://docs.jboss.org/netty/3.1/api/org/jboss/netty/util/HashedWheelTimer.html) –
    a potential precision loss for jobs, as it does not provide strong guarantees on the timeliness of execution.

2. Why not just use the Quartz component in [Akka's Camel Extension](http://doc.akka.io/docs/akka/2.1.2/scala/camel.html)?

    1. To begin with, Akka's Camel extension was *not* available in Akka 2.0.x, only in 2.1+
    2. Camel brings with it a whole architecture change (`Consumers`, `Producers`, etc) and is not exactly "lightweight" to plug in if all you want is Quartz support.
    3. We wanted to bring the scheduling concept of Quartz into Akka as cleanly as possible with native configuration integration and a lightweight feel.

3. What about that other `akka-quartz` component up on GitHub?

    The interface to this aforementioned `akka-quartz` component is via Actors - one creates an instance of an Actor that 
    has its own Quartz Scheduler underneath it, and sends messages to that Actor to schedule jobs. Because it is an Actor
    which provides no "Singleton"-like guarantee, it becomes too easy for users to accidentally spin up multiple scheduler
    instances, each of which is backed by its own threadpool.
    Instead, with `akka-quartz-scheduler` we use Akka's Extension system which provides
    a plugin model – we guarantee only one Quartz Scheduler is *ever* spun up per `ActorSystem`. This means
    we will never create anything but one single Thread Pool which you have control over the size of, for
    any given `ActorSystem`.

Finally, a common failure of the above listed alternatives is that configuration of things like a repeating schedule
should be separated from code in a configuration file which an operations team (not the developers) can
control. Thus, `akka-quartz-scheduler` only allows specifying the following in code: the name of a job, what actor to send
the tick to, and the message to send on a tick. The configuration of how frequently to 'tick' on a schedule is
externalised to the Akka configuration file; when a schedule request is made its name is matched up with an entry
in the config which specifies the rules the actual scheduling should follow.

Thus, development can outline the skeleton of repeating jobs in their code, specifying only what to do WHEN a 'tick' of
the schedule fires. Then, operations has complete control over how often a job runs and what rules it follows to determine
the schedule of firing.

This, among other things, prevents accidental mistakes such as changing a schedule in development for testing. A
change of that sort is fixable without Operations needing to require a recompilation of source code.

### TODO
- investigate supporting listeners, with actor hookarounds.
- misfires and recovery model - play nice with supervision, deathwatch, etc
  [docs page 23 - very close to supervision strategy]

## Usage

Usage of the `akka-quartz-scheduler` component first requires including the necessary dependency in your SBT project:

```
// For Akka 2.3.x and Scala 2.11.x
libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.5.0-akka-2.3.x"
```

```
//Older versions of the artifact for those that require pre Akka 2.3 or pre Scala 2.11
//If you would like a current version of the artifact to be published for your required version
//of Akka and Scala, simply file on issue on the project page.
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


// For Akka 2.0.x
libraryDependencies += "com.typesafe.akka" %% "akka-quartz-scheduler" % "1.2.0-akka-2.0.x"
// For Akka 2.1.x
libraryDependencies += "com.typesafe.akka" %% "akka-quartz-scheduler" % "1.2.0-akka-2.1.x"
// For Akka 2.2.x
libraryDependencies += "com.typesafe.akka" %% "akka-quartz-scheduler" % "1.2.0-akka-2.2.x"
// For Akka 2.3.x and Scala 2.10.4
libraryDependencies += "com.typesafe.akka" % "akka-quartz-scheduler_2.10" % "1.4.0-akka-2.3.x"
```

Note that the version name includes the Akka revision (Previous releases included the Akka release in the artifact name, which broken Maven).

Then, from within your Akka project you can create and access a Scheduler:

```scala
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

val scheduler = QuartzSchedulerExtension(system)

```

Where `system` represents an instance of an Akka `ActorSystem` – note that `QuartzSchedulerExtension` is scoped
to that `ActorSystem` and there will only ever be one instance of it per `ActorSystem`.

The primary external method on the `scheduler` instance is `schedule`, used for scheduling a job:

```scala
def schedule(name: String, receiver: ActorRef, msg: AnyRef, startDate: Option[Date]): java.util.Date
```
OR
```scala
def schedule(name: String, receiver: ActorSelection, msg: AnyRef, startDate: Option[Date]): java.util.Date
```

The arguments to schedule are:

- `name`: A `String` identifying the name of this schedule. This *must* match a schedule present in the configuration
- `receiver`: An `ActorRef` or `ActorSelection`, who will be sent `msg` each time the schedule fires
- `msg`: An `AnyRef`, representing a message instance which will be sent to `receiver` each time the schedule fires
- `startDate`: An optional `Date`, for postponed start of a job. Defaults to now. 

Invoking `schedule` returns an instance of `java.util.Date`, representing the first time the newly setup schedule
will fire.

Each time the Quartz schedule trigger fires, Quartz will send a copy of `msg` to your `receiver` actor.

Here is an example, using a schedule called `Every30Seconds`, which sends a `Tick` message to a `CleanupActor` (which does hand wavy cleanup things):

```scala
case object Tick

val cleaner = system.actorOf(Props[CleanupActor])

QuartzSchedulerExtension(system).schedule("Every30Seconds", cleaner, Tick)
```

Where the `Tick` message is handled normally inside the Actor's message loop. If one wanted to ensure that schedule
messages were dealt with more immediately than "normal" actor messages, they could utilize [Priority Mailboxes](http://doc.akka.io/docs/akka/2.4.1/scala/dispatchers.html).

The details on the configuration of a job is outlined below in the section '*Schedule Configuration*'.


### Configuration of Quartz Scheduler

All configuration of `akka-quartz-scheduler` is done inside of the Akka configuration file in an `akka.quartz` config
block. Like Akka's configuration file, this follows the [HOCON Configuration Format](https://github.com/typesafehub/config/blob/master/HOCON.md).
Thus, any entries specified as `foo.bar.baz = x` can also be expressed as `foo { bar { baz = x } }`.

At the top level of the configuration, optional values may be set which override the defaults for:

- `defaultTimezone` - **[String]** represents the timezone to configure all jobs to run in. *DEFAULTS TO UTC*
must be parseable by [`java.util.TimeZone.getTimeZone()`](http://docs.oracle.com/javase/7/docs/api/java/util/TimeZone.html#getTimeZone(java.lang.String)),
- `threadPool.threadCount` - **[Int]** The number of threads to allocate to the internal Quartz threadpool. *DEFAULTS TO 1* - you may wish to up this number if you have a large number of schedules
being executed. With only 1 thread, each trigger will queue up and you may not get responsive schedule notifications.
- `threadPool.threadPriority` - **[Int]** A number, between 1 (Lowest priority) and 10 (Highest priority), specifying the priority to assign to Quartz' threads *DEFAULTS TO 5*
- `threadPool.daemonThreads` - **[Boolean]** A boolean indicating whether the threads Quartz creates should execute as [Daemon Threads](http://stackoverflow.com/a/2213348) or not. *DEFAULTS TO TRUE*

There are two 'primary' sub-blocks of the `akka.quartz` configuration, which are `schedules` and `calendars`.

#### Schedule Configuration

Schedules are our abstraction over Quartz' Job & Trigger concepts. They allow you to define a named schedule,
which will fire a schedule event (sending a message to an actor, as specified in code) each time the Quartz trigger fires.

Currently, you can only specify "Cron" schedules, which follow [Quartz' CronExpression Language](http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html),
which is designed to match the standard Unix cron syntax with a few nice additions.

The schedule name in the configuration will be used to match it up with a requested job when `schedule` is invoked;
case does not matter as the "Is there a matching job?" configuration lookup is case insensitive.

The configuration block for schedules is in `akka.quartz.schedules`, with sub-entries being specified inside of a named
block, such that the configuration for a schedule named `Every30Seconds` would have its configuration values specified inside
the configuration block `akka.quartz.schedules.Every30Seconds`.

The entries that can be placed inside of a schedule configuration are:

- `expression` - **[String]** *[required]* a valid [Quartz' CronExpression](http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html),
which describes when this job should trigger. e.g. `expression = "*/30 * * ? * *"` would fire every 30 seconds, on every date (however,
the firing schedule created by this expression is modified by the `calendars` variable, defined below)
- `timezone` - **[String]** *[optional]*  the timezone in which to execute the schedule, *DEFAULTS TO `akka.quartz.defaultTimezone`, WHICH DEFAULTS TO **UTC***
must be parseable by [`java.util.TimeZone.getTimeZone()`](http://docs.oracle.com/javase/7/docs/api/java/util/TimeZone.html#getTimeZone(java.lang.String))
- `description` - **[String]** *[optional]* a description of the job. *DEFAULTS TO null*. Mostly for human friendliness
when they read your configuration aka "what this schedule is for", but set in Quartz as well for if you dump the scheduler contents
for debug.
- `calendar` - **[String]** *[optional]* An option String which is the name of a configured Calendar.  This Calendar is applied
to this schedule as "exemptions" (Any times/dates falling in the Calendar will be excluded from the schedule firing - i.e.
a Calendar that excludes all Mondays would keep a schedule configured to trigger every hour, from triggering *at all* on Mondays.
NOTE: In versions 1.3.x and prior, this property was "calendars" and supported a list of Strings.  However, Quartz, and by proxy, this library
never actually supported multiple calendars for one schedule.  Therefore, in versions 1.4.x and beyond this property has been renamed to "calendar"
and is an optional String.
*DEFAULTS TO **None[String]***

An example schedule called `Every30Seconds` which, aptly, fires off every 30 seconds:

```
akka {
  quartz {
    schedules {
      Every30Seconds {
        description = "A cron job that fires off every 30 seconds"
        expression = "*/30 * * ? * *"
        calendar = "OnlyBusinessHours"
      }
    }
  }
}
```
This Schedule specifies a Cron Expression which executes every 30 seconds of every day, but is modified by the calendar
"OnlyBusinessHours", which excludes triggers from firing outside of between 8am and 6pm (and is detailed below).


#### Calendar Configuration

Calendars in the `akka-quartz-scheduler` mirror the concept of [Quartz' Calendars](http://quartz-scheduler.org/documentation/quartz-2.x/tutorials/tutorial-lesson-04) –
most specifically, they allow you to specify *exclusions* that override a schedule.

Calendars are configured globally, in the `akka.quartz.calendars` configuration block. The definition of a calendar and what it excludes
is made within this block. By default, no Calendars are applied to a Schedule. Instead, you must reference a named Calendar
inside the `calendars` array of a Schedule's configuration, as outlined above.

The configuration block for calendars is in `akka.quartz.calendars`, with sub-entries being specified inside of a named
block, such that the configuration for a calendar named `OnlyBusinessHours` would have it's configuration values specified inside
the configuration block `akka.quartz.calendars.OnlyBusinessHours`.

There are several types of Calendar, each with its own specific configurations. The configuration values which are common to *all* types of Calendar are:

- `type` - **[String]** *[required]* a valid type of Calendar. Currently either: Annual, Holiday, Daily, Monthly, Weekly, and Cron
- `timezone` - **[String]** *[optional]*  the timezone in which to execute the calendar, *DEFAULTS TO `akka.quartz.defaultTimezone`, WHICH DEFAULTS TO **UTC***
must be parseable by [`java.util.TimeZone.getTimeZone()`](http://docs.oracle.com/javase/7/docs/api/java/util/TimeZone.html#getTimeZone(java.lang.String))
- `description` - **[String]** *[optional]* a description of the calendar. *DEFAULTS TO null*. Mostly for human friendliness
when they read your configuration aka "what this calendar is for", but set in Quartz as well for if you dump the scheduler contents
for debug.

Each specific Calendar type and its particular configuration entries are...

#####  'Annual' Calendar
An annual calendar excludes specific days of a given year, e.g. bank holidays which fall on the same date every year (Christmas and Gregorian New Year's for example)
This calendar *does not take year into account*, and will apply to all years.

It has only one configuration entry:

- `excludeDates` - **[Seq[String]]** *[required]* This is a list of strings which are dates in the MM-DD format, representing which dates to exclude.
 For example, "Exclude Christmas and New Years" would read as `excludeDates = ["12-25", "01-01"]`

An example:

```
WinterClosings {
  type = Annual
  description = "Major holiday dates that occur in the winter time every year, non-moveable (The year doesn't matter)"
  excludeDates = ["12-25", "01-01"]
}
```

##### 'Holiday' Calendar
A holiday calendar excludes specific dates, with a fully month, day, year taken into account. It is mostly useful for
moving Bank Holidays (e.g. President's Day) and Moveable Feasts (e.g. Easter, which is based on a Lunar calendar).

It has only one configuration entry:

- `excludeDates` - **[Seq[String]]** *[required]* This is a list of strings in the ISO-8601 date format (YYYY-MM-DD), representing which dates
(with year taken into account) to exclude. For example, excluding the the next 5 years' Easter holidays would read as `excludeDates = ["2013-03-31", "2014-04-20", "2015-04-05", "2016-03-27", "2017-04-16"]`

An example:

```
Easter {
  type = Holiday
  description = "The easter holiday (a moveable feast) for the next five years"
  excludeDates = ["2013-03-31", "2014-04-20", "2015-04-05", "2016-03-27", "2017-04-16"]
}
```

##### 'Daily' Calendar
A daily calendar excludes a specified time range each day. It *may not* cross daily boundaries, and Quartz will enforce this.
i.e. You cannot specify "11PM to 1AM" – to do that you'll need to specify two separate daily calendars.

Exclusions in a Daily calendar are specified in a `exclude` with a `startTime` and `endTime` entry. Each of these fields
follows a time format of `HH:MM[:SS[:mmm]]` where:
    - HH is the hour of the specified time using military (24-hour) time, and must be in the range 0-23
    - MM is the minute of the specified time, and must be in the range 0-59
    - SS is the **optional** second of the specified time, and must be in the range 0-59
    - mmm is the **optional** millisecond of the specified time, and must be in the range 0-999

An example, which  doesn't allow jobs to run between 3AM and 5AM during the PST Timezone:

```
HourOfTheWolf {
  type = Daily
  description = "A period every day in which cron jobs are quiesced, during night hours"
  exclude {
    startTime = "03:00"
    endTime   = "05:00:00"
  }
  timezone = PST
}
```

##### 'Monthly' Calendar
A monthly calendar excludes a set of days of the month, i.e. "Don't run a job on the 1st or 15th days of the month"

It has only one configuration entry:

- `excludeDays` - **[Seq[Int]]** *[required]* This is a list of Ints, between 1 and 31, representing a day of the month.

An example:

```
FirstAndLastOfMonth {
  type = Monthly
  description = "A thinly veiled example to test monthly exclusions"
  excludeDays = [1, 31]
}
```

##### 'Weekly' Calendar
A weekly calendar excludes a set of days of the week. *By default, Saturday and Sunday are always excluded*

The configuration entries:

- `excludeDays` - **[Seq[Int]]** *[required]* This is a list of Ints, between 1 and 7 – where 1 is Sunday and 7 is Saturday – representing days of the week to exclude.
- `excludeWeekends` - **Boolean** *defaults to TRUE* Whether weekends should be excluded automatically by this scheduler or not. Note that `excludeWeekends` is overriden
by `excludeDays` – if you specify `excludeWeekends = false` but `excludeDays` includes Sunday (`1`) or Saturday (`7`), then a configuration error will be thrown.

An example, which excludes jobs from running on any Monday:
```
MondaysSuck {
  type = Weekly
  description = "Everyone, including this calendar, hates mondays as an integer"
  excludeDays = [2]
  excludeWeekends = false
}
```
Note that by default, `excludeWeekends` would be true and thus `excludeDays` would implicitly be `[1, 2, 7]`

##### 'Cron' Calendar
A cron calendar excludes the set of times expressed by a given [Quartz CronExpression](http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html).

It has only one configuration entry:

- `excludeExpression` - **[String]** *[required]* A valid [Quartz CronExpression](http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html), which will be
used to specify what times a job *cannot* run in (the opposite of a Cron Schedule).

An example Calendar, which specifies an exclusion set of `00:00 - 07:59` and `18:00 - 23:59` (thereby only allowing jobs to run from `08:00 - 17:59`):

```
CronOnlyBusinessHours {
  type = Cron
  excludeExpression = "* * 0-7,18-23 ? *
}
```


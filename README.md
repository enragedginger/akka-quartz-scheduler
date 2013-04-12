akka-quartz-scheduler
=====================

Quartz Extension and utilities for true scheduling in Akka 2.0+ (right now built against 2.0.x, but will target
all versions eventually)

Note that this is named and targeted as akka-quartz-scheduler for a reason: it is *not* a complete port of Quartz.
Rather, we utilize the concepts of Quartz' scheduling system to provide a more robust and reliable scheduling component
to Akka than the one already available.

The goal here is to provide Akka with a scheduling system that is closer to what one would expect for Cron type jobs –
setup long-running ActorSystems that can have certain events kick off by Quartz.

There aren't currently any plans on having anything to do with the distributed transaction, persistence,
clustering or any other nonsense anytime soon. This is for cron-ey type timing and scheduling.

There is the ability in Quartz to pass JobDataMaps around that accrue mutable state across job ticks;
we currently do NOT support that to enforce integrity, but may expose a deeper actor structure later that
gives some ability to work around that, if need arises.

Evolving, subject to change, and not curently for public consumption.

## Why Not Use $OtherComparableTool Instead?

1. What's wrong with Akka's existing Scheduler?
    As Viktor Klang points out, 'Perhaps the name "Scheduler" was unfortunate,
    "Deferer" is probably more indicative of what it does.'

    The Akka Scheduler is designed to setup events that happen based on durations from the current moment:
    You can say "fire this job in 15 minutes, every 30 minutes thereafter" but not "fire a job every day at 3pm".

    Further, Akka's default scheduler also is executed around a HashedWheelTimer – a potential precision loss for jobs,
    as it does not provide strong guarantees on the timeliness of execution.

2. Why not just use the Quartz component in Akka's Camel Extension?

  1. To begin with, Akka's Camel extension was *not* available in Akka 2.0.x, only in 2.1+
  2. Camel brings with it a whole architecture change (`Consumers`, `Producers`, etc) and is not exactly "lightweight" to plug in, if all you want is Quartz support
  3. We wanted to bring the scheduling concept of Quartz into Akka as cleanly as possible with native configuration integration and a lightweight feel

3. What about that other `akka-quartz` component up on GitHub?

    The interface to this aforementioned `akka-quartz` component is via Actors - one creates an instance of an Actor that 
    has its own Quartz Scheduler underneath it, and sends messages to that Actor to schedule jobs. Because it is an Actor
    which provides no "Singleton"-like guarantee, it becomes too easy for users to accidentally spin up multiple scheduler
    instances, each of which is backed by its  own threadpool.
    Instead, with `akka-quartz-scheduler` we use Akka's Extension system which provides
    a plugin model – we guarantee only one Quartz Scheduler is *ever* spun up per `ActorSystem`. This means
    we will never create anything but one single Thread Pool which you have control over the size of, for
    any given `ActorSystem`.

Finally, a common failure of the above listed alternatives is that configuration of things like a repeating schedule
should be separated from code: in a configuration file which an operations team (not the developers) can have
control. Thus, `akka-quartz-scheduler` allows only allows specifiyng in code: the name of a job, what actor to send
the tick to, and the message to send on a tick. The configuration of how frequently to 'tick' on a schedule is
externalised to the Akka configuration file; when a schedule request is made its name is matched up with an entry
in the config which specifies the rules the actual scheduling should follow.

Thus, Development can outline the skeleton of repeating jobs in their code, specifying only what to do WHEN a 'tick' of
the schedule fires. Then, Operations has complete control over how often a job runs and what rules it follows to determine
the schedule of firing.

This, among other things, prevents accidental mistakes such as changing a schedule in development for testing. A
change of that sort is fixable without Operations needing to require a recompilation of source code.

### TODO
- ensure the ActorSystem shutdown can shut the scheduler in the extension down
- investigate supporting listeners, with actor hookarounds.
- misfires and recovery model - play nice with supervision, deathwatch, etc
  [docs page 23 - very close to supervision strategy]
- allow specification of delayed start time
- permit cancellation, suspension and status checks of schedules

## Usage

Usage of the `akka-quartz-scheduler` component first requires including the necessary dependency in your SBT project:

        <PENDING>

Then, from within your Akka project you can create and access a Scheduler:

    ```scala
    val scheduler = QuartzSchedulerExtension(_system)

    ```

Where `_system` represents an instance of an Akka `ActorSystem` – note that `QuartzSchedulerExtension` is scoped
to that `ActorSystem` and there will only ever be one instance of it per `ActorSystem`.

There is only one external method on the `scheduler` instance, which is `schedule`:

    ```scala
    def schedule(name: String, receiver: ActorRef, msg: AnyRef): java.util.Date
    ```

The arguments to schedule are:

- `name`: A `String` identifying the name of this schedule. This *must* match a schedule present in the configuration
- `receiver`: An `ActorRef`, who will be sent `msg` each time the schedule fires
- `msg`: An `AnyRef`, representing a message instance which will be sent to `receiver` each time the schedule fires

Invoking `schedule` returns an instance of `java.util.Date`, representing the first time the newly setup schedule
will fire.

Each time the Quartz schedule trigger fires, Quartz will send a copy of `msg` to your `receiver` actor.

The details on the configuration of a job is outlined below in the section `Schedule Configuration`.

### Configuration of Quartz Scheduler

All configuration of `akka-quartz-scheduler` is done inside of the akka configuration file in an `akka.quartz` config
block. Like Akka's configuration file, this follows the [HOCON Configuration Format](https://github.com/typesafehub/config/blob/master/HOCON.md).
Any keys specified as `foo.bar.baz = x` can also be expressed as `foo { bar { baz = x } }`.

At the top level of the configuration, optional values may be set which override the defaults for:

- `defaultTimezone` - **[String]** represents the timezone to configure all jobs to run in. *DEFAULTS TO **UTC***
must be parseable by [`java.util.TimeZone.getTimeZone()`](http://docs.oracle.com/javase/7/docs/api/java/util/TimeZone.html#getTimeZone(java.lang.String)),
- `threadPool.threadCount` - **[Int]** The number of threads to allocate to the internal Quartz threadpool. *DEFAULTS TO **1*** - you may wish to up this number if you have a large number of schedules
being executed. With only 1 thread, each trigger will queue up and you may not get responsive schedule notifications.
- `threadPool.threadPriority` - **[Int]** A number, between 1 (Lowest priority) and 10 (Highest priority), specifying the priority to assign to Quartz' threads *DEFAULTS TO **5***
- `threadPool.daemonThreads` - **[Boolean]** A boolean indicating whether the threads Quartz creates should execute as [Daemon Threads](http://stackoverflow.com/a/2213348) or not. *DEFAULTS TO **TRUE***

There are two 'primary' sub-blocks of the `akka.quartz` configuration, which are `schedules` and `calendars`.

#### Schedule Configuration

Schedules are our abstraction over Quartz' Job & Trigger concepts. They allow you to define a named schedule,
which will fire a schedule event (sending a message to an actor, as specified in code) each time the Quartz trigger fires.

Currently, you can only specify "Cron" schedules, which follow [Quartz' CronExpression Language](http://quartz-scheduler.org/api/2.1.0/org/quartz/CronExpression.html),
which is designed to match the standard Unix cron syntax with a few nice additions.

The schedule name in the configuration will be used to match it up with a requested job when `schedule` is invoked;
case does not matter as the "Is there a matching job?" configuration lookup is case insensitive.

The configuration block for schedules is in `akka.quartz.schedules`, with sub-entries being specified inside of a named
block, such that the configuration for a schedule named '3AMCleanup' would have it's configuration values specified inside
the configuration block `akka.quartz.schedules.3AMCleanup`.

The entries that can be placed inside of a schedule configuration are:

- `expression` - **[String]** *[required]* a valid [Quartz' CronExpression](http://quartz-scheduler.org/api/2.1.0/org/quartz/CronExpression.html),
which describes when this job should trigger. e.g. `expression = "*/30 * * ? * *"` would fire every 30 seconds, on every date (however,
the firing schedule created by this expression is modified by the `calendars` variable, defined below)
- `timezone` - **[String]** *[optional]*  the timezone in which to execute the schedule, *DEFAULTS TO `akka.quartz.defaultTimezone`, WHICH DEFAULTS TO **UTC***
must be parseable by [`java.util.TimeZone.getTimeZone()`](http://docs.oracle.com/javase/7/docs/api/java/util/TimeZone.html#getTimeZone(java.lang.String))
- `description` - **[String]** *[optional]* a description of the job. *DEFAULTS TO **null**. Mostly for human friendliness
when they read your configuration aka "what this schedule is for", but set in Quartz as well for if you dump the scheduler contents
for debug.
- `calendars` - **[List[String]]** *[optional]* A HOCON List of Strings, which are names of configured Calendars (see below), which are applied
to this schedule as "exemptions" (Any times/dates falling in the Calendar will be excluded from the schedule firing - i.e.
a Calendar that excludes all Mondays would keep a schedule configured to trigger every hour, from triggering *at all* on Mondays.
*DEFAULTS TO **Seq.empty[String]***

#### Calendar Configuration



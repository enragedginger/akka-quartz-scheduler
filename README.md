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




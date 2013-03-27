akka-quartz
===========

Quartz Extension and utilities for true scheduling in Akka

As Viktor Klang points out, 'Perhaps the name "Scheduler" was unfortunate, "Deferer" is probably more indicative of what it does.'

The Akka Scheduler is designed to setup events that happen based on durations from the current moment, but also is executed around a HashedWheelTimer – a potential precision loss for jobs.

The goal here is to provide Akka with a scheduling system that is closer to what one would expect for Cron type jobs – setup long-running ActorSystems that can have certain events kick off by Quartz.

Evolving, subject to change, and not curently for public consumption.



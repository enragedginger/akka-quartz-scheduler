package com.typesafe.akka.extension.quartz

import java.util.{Date, TimeZone}

import akka.actor.ExtendedActorSystem
import akka.actor.typed.{ActorRef, ActorSystem, Extension, ExtensionId}

object QuartzSchedulerTypedExtension extends ExtensionId[QuartzSchedulerTypedExtension] {

  override def createExtension(system: ActorSystem[_]): QuartzSchedulerTypedExtension =
    new QuartzSchedulerTypedExtension(system)

  // Java API: retrieve the extension instance for the given system.
  def get(system: ActorSystem[_]): QuartzSchedulerTypedExtension = apply(system)

}

class QuartzSchedulerTypedExtension(system: ActorSystem[_]) extends QuartzSchedulerExtension(system.classicSystem) with Extension {

  /**
   * Creates job, associated triggers and corresponding schedule at once.
   *
   *
   * @param name The name of the job, as defined in the schedule
   * @param receiver An ActorRef, who will be notified each time the schedule fires
   * @param msg A message object, which will be sent to `receiver` each time the schedule fires
   * @param description A string describing the purpose of the job
   * @param cronExpression A string with the cron-type expression
   * @param calendar An optional calendar to use.
   * @param timezone The time zone to use if different from default.

   * @return A date which indicates the first time the trigger will fire.
   */
  def createTypedJobSchedule[T](
                         name: String, receiver: ActorRef[T], msg: T, description: Option[String] = None,
                         cronExpression: String, calendar: Option[String] = None, timezone: TimeZone = defaultTimezone) = {
    createSchedule(name, description, cronExpression, calendar, timezone)
    scheduleTyped(name, receiver, msg)
  }

  /**
   * Updates job, associated triggers and corresponding schedule at once.
   *
   *
   * @param name The name of the job, as defined in the schedule
   * @param receiver An ActorRef, who will be notified each time the schedule fires
   * @param msg A message object, which will be sent to `receiver` each time the schedule fires
   * @param description A string describing the purpose of the job
   * @param cronExpression A string with the cron-type expression
   * @param calendar An optional calendar to use.
   * @param timezone The time zone to use if different from default.

   * @return A date which indicates the first time the trigger will fire.
   */
  def updateTypedJobSchedule[T](
                         name: String, receiver: ActorRef[T], msg: T, description: Option[String] = None,
                         cronExpression: String, calendar: Option[String] = None, timezone: TimeZone = defaultTimezone): Date = {
    rescheduleTypedJob(name, receiver, msg, description, cronExpression, calendar, timezone)
  }

  /**
   * Reschedule a job
   *
   * @param name           A String identifying the job
   * @param receiver       An ActorRef, who will be notified each time the schedule fires
   * @param msg            A message object, which will be sent to `receiver` each time the schedule fires
   * @param description    A string describing the purpose of the job
   * @param cronExpression A string with the cron-type expression
   * @param calendar       An optional calendar to use.
   * @return A date which indicates the first time the trigger will fire.
   */

  def rescheduleTypedJob[T](name: String, receiver: ActorRef[T], msg: T, description: Option[String] = None,
                    cronExpression: String, calendar: Option[String] = None, timezone: TimeZone = defaultTimezone): Date = {
    cancelJob(name)
    removeSchedule(name)
    createSchedule(name, description, cronExpression, calendar, timezone)
    scheduleInternal(name, receiver, msg.asInstanceOf[AnyRef], None)
  }

  /**
   * Schedule a job, whose named configuration must be available
   *
   * @param name     A String identifying the job, which must match configuration
   * @param receiver An ActorRef, who will be notified each time the schedule fires
   * @param msg      A message object, which will be sent to `receiver` each time the schedule fires
   * @return A date which indicates the first time the trigger will fire.
   */
  def scheduleTyped[T](name: String, receiver: ActorRef[T], msg: T): Date = scheduleInternal(name, receiver, msg.asInstanceOf[AnyRef], None)

  /**
   * Schedule a job, whose named configuration must be available
   *
   * @param name     A String identifying the job, which must match configuration
   * @param receiver An ActorRef, who will be notified each time the schedule fires
   * @param msg      A message object, which will be sent to `receiver` each time the schedule fires
   * @return A date which indicates the first time the trigger will fire.
   */
  def scheduleTyped[T](name: String, receiver: ActorRef[T], msg: T, startDate: Option[Date]): Date = scheduleInternal(name, receiver, msg.asInstanceOf[AnyRef], startDate)

}

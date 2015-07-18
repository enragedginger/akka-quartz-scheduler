package com.typesafe.akka.extension.quartz

import com.typesafe.config.{ConfigObject, ConfigException, Config}
import java.util.TimeZone
import scala.util.control.Exception._
import org.quartz._
import collection.immutable
import java.text.ParseException

import scala.collection.JavaConverters._
import scala.Some
import scala.annotation.tailrec

/**
 * This is really about triggers - as the "job" is roughly defined in the code that
 *  refers to the trigger.
 *
 *  I call them Schedules to get people not thinking about Quartz in Quartz terms (mutable jobs, persistent state)
 *
 *  All jobs "start" immediately.
 */
object QuartzSchedules {
  // timezone (parseable) [optional, defaults to UTC]
  // calendars = list of calendar names that "modify" this schedule
  // description = an optional description of the job [string] [optional]
  // expression = cron expression complying to Quartz' Cron Expression rules.
  // TODO - Misfire Handling

  val catchMissing = catching(classOf[ConfigException.Missing])
  val catchWrongType = catching(classOf[ConfigException.WrongType])
  val catchParseErr = catching(classOf[ParseException])

  def apply(config: Config, defaultTimezone: TimeZone): immutable.Map[String, QuartzSchedule] = catchMissing opt {
    /** The extra toMap call is because the asScala gives us a mutable map... */
    config.getConfig("schedules").root.asScala.toMap.flatMap {
      case (key, value: ConfigObject) =>
        Some(key -> parseSchedule(key, value.toConfig, defaultTimezone))
      case _ =>
        None
    }
  } getOrElse immutable.Map.empty[String, QuartzSchedule]

  def parseSchedule(name: String, config: Config, defaultTimezone: TimeZone): QuartzSchedule = {
    // parse common attributes
    val timezone = catchMissing opt {
      TimeZone.getTimeZone(config.getString("timezone")) // todo - this is bad, as Java silently swaps the timezone if it doesn't match...
    } getOrElse defaultTimezone

    val calendar = catchMissing opt {
      Option(config.getString("calendar")) // TODO - does Quartz validate for us that a calendar referenced is valid/invalid?
    } getOrElse None

    val desc = catchMissing opt {
      config.getString("description")
    }

    parseCronSchedule(name, desc, config)(timezone, calendar)
  }

  def parseCronSchedule(name: String, desc: Option[String], config: Config)(tz: TimeZone, calendar: Option[String]): QuartzCronSchedule = {
    val expression = catchMissing or catchWrongType either { config.getString("expression") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'expression' for Cron Schedule '%s'. You must provide a valid Quartz CronExpression.".format(name), t)
      case Right(str) => catchParseErr either new CronExpression(str) match {
        case Left(t) =>
          throw new IllegalArgumentException("Invalid 'expression' for Cron Schedule '%s'. Failed to validate CronExpression.".format(name), t)
        case Right(expr) => expr
      }
    }
    new QuartzCronSchedule(name, desc, expression, tz, calendar)
  }
}

sealed trait QuartzSchedule {
  type T <: Trigger

  def name: String

  def description: Option[String]

  // todo - I don't like this as we can't guarantee the builder's state, but the Quartz API forces our hand
  def schedule: ScheduleBuilder[T]

  //The name of the optional exclusion calendar to use.
  //NOTE: This formerly was "calendars" but that functionality has since been removed as Quartz never supported more
  //than one calendar anyways.
  def calendar: Option[String]

  /**
   * Utility method that builds a trigger
   * with the data this schedule contains, given a name
   * Job association can happen separately at schedule time.
   */
  def buildTrigger(name: String): T = {
    var triggerBuilder = TriggerBuilder.newTrigger()
                           .withIdentity(name + "_Trigger")
                           .withDescription(description.orNull)
                           .startNow()
                           .withSchedule(schedule)
    triggerBuilder = calendar.map(triggerBuilder.modifiedByCalendar).getOrElse(triggerBuilder)
    triggerBuilder.build()
  }

}

final class QuartzCronSchedule(val name: String,
                               val description: Option[String] = None,
                               val expression: CronExpression,
                               val timezone: TimeZone,
                               val calendar: Option[String] = None) extends QuartzSchedule {

  type T = CronTrigger

  // Do *NOT* build, we need the uncompleted builder. I hate the Quartz API, truly.
  val schedule = CronScheduleBuilder.cronSchedule(expression).inTimeZone(timezone)
}


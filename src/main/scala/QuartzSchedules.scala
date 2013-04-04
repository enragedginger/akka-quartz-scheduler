package akka.extension.quartz

import com.typesafe.config.{ConfigObject, ConfigException, Config}
import java.util.TimeZone
import scala.util.control.Exception._
import org.quartz._
import org.quartz.impl.calendar._
import collection.immutable
import java.text.ParseException

import scala.collection.JavaConverters._
import scala.Some
import spi.MutableTrigger

/**
 * This is really about triggers - as the "job" is roughly defined in the code that
 *  refers to the trigger.
 *
 *  I call them Schedules to get people not thinking about Quartz in Quartz terms (mutable jobs, persistent state)
 *
 *  All jobs "start" immediately.
 */
object QuartzSchedules {
  // type (Simple, Cron)
  // timezone (parseable) [optional, defaults to UTC] TODO: Default Timezone at toplevel
  // calendars = list of calendar names that "modify" this schedule
  // TODO - Misfire Handling

  /* simple
   *
   * A "simple" schedule with some basic attributes similar to the default akka scheduler
   */
  // repeat block {
  //    scale = HOURS |  MINUTES | SECONDS | MILLISECONDS
  //    interval = INT
  //    forever = <BOOLEAN> [Default TRUE] [optional]
  //    count = INT [overrides repeatForever] [optional]
  // }


  /* cron
   *
   * a "cron" type scheduler using Quartz' Cron Expressions system.
   */
  // expression = cron expression complying to Quartz' Cron Expression rules.

  val catchMissing = catching(classOf[ConfigException.Missing])
  val catchWrongType = catching(classOf[ConfigException.WrongType])
  val catchParseErr = catching(classOf[ParseException])

  def apply(config: Config, defaultTimezone: TimeZone): immutable.Map[String, QuartzSchedule] = catchMissing opt {
    /** The extra toMap call is because the asScala gives us a mutable map... */
    config.getConfig("akka.quartz.schedules").root.asScala.toMap.flatMap {
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

    val calendars = catchMissing opt {
      config.getStringList("calendars").asScala // TODO - does Quartz validate for us that a calendar referenced is valid/invalid?
    } getOrElse Seq.empty[String]

    catchMissing either { config.getString("type") } match {
      case Left(_) => throw new IllegalArgumentException("Schedule Type must be defined for " + name)
      case Right(typ) => typ.toUpperCase match {
        case "CRON" => parseCronSchedule(name, config)(timezone, calendars)
        case "SIMPLE" => parseSimpleSchedule(name, config)(calendars)
        case other =>
          throw new IllegalArgumentException("Unknown Quartz Schedule type '%s' for calendar '%s'. Valid types are Cron and Simple.".format(other, name))
      }
    }
  }

  def parseSimpleSchedule(name: String, config: Config)(calendars: Seq[String]): QuartzSimpleSchedule = {
    //repeat block {
    //    scale = HOURS |  MINUTES | SECONDS | MILLISECONDS
    //    interval = INT
    //    forever = <BOOLEAN> [Default TRUE] [optional]
    //    count = INT [overrides repeatForever] [optional]
    // }
    val interval = catchMissing or catchWrongType either { config.getInt("repeat.interval") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'repeat.interval' for Simple Schedule '%s'".format(name) +
          " – You must provide an Integer representing the time interval", t)
      case Right(i) => i
    }

    val repeat: Either[Boolean, Int] = catchMissing opt { config.getInt("repeat.count") } match {
      case Some(i) => Right(i)
      case None => catchMissing opt { config.getBoolean("repeat.forever") } match {
        case Some(b) => Left(b)
        case None => Left(true)
      }
    }

    catchMissing or catchWrongType either { config.getString("repeat.scale") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'repeat.scale' for Simple Schedule '%s'".format(name) +
                                           " – You must provide a scale of either Hours, Minutes, Seconds, or Milliseconds.", t)
      case Right(scale) => scale.toUpperCase match {
        case "HOURS" => new QuartzSimpleHourlySchedule(name, interval, repeat, calendars)
        case "MINUTES" => new QuartzSimpleMinutelySchedule(name, interval, repeat, calendars)
        case "SECONDS" => new QuartzSimpleSecondlySchedule(name, interval, repeat, calendars)
        case "MILLISECONDS" => new QuartzSimpleMillisecondlySchedule(name, interval, repeat, calendars)
        case other =>
          throw new IllegalArgumentException("Invalid 'repeat.scale' type for Simple Schedule '%s'".format(name) +
                                             " – Valid scales are Hours, Minutes, Seconds, Milliseconds.")
      }
    }
  }

  def parseCronSchedule(name: String, config: Config)(tz: TimeZone, calendars: Seq[String]): QuartzCronSchedule = {
    val expression = catchMissing or catchWrongType either { config.getString("expression") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'expression' for Cron Schedule '%s'. You must provide a valid Quartz CronExpression.".format(name), t)
      case Right(str) => catchParseErr either new CronExpression(str) match {
        case Left(t) =>
          throw new IllegalArgumentException("Invalid 'expression' for Cron Schedule '%s'. Failed to validate CronExpression.".format(name), t)
        case Right(expr) => expr
      }
    }
    new QuartzCronSchedule(name, expression, tz, calendars)
  }
}

sealed trait QuartzSchedule {
  //type T <: Trigger

  def name: String

  def schedule: MutableTrigger

  def calendars: Seq[String] // calendars that modify this schedule

}

final class QuartzCronSchedule(val name: String,
                               val expression: CronExpression,
                               val timezone: TimeZone,
                               val calendars: Seq[String]) extends QuartzSchedule {

  val schedule = CronScheduleBuilder.cronSchedule(expression).inTimeZone(timezone).build()
}

sealed abstract class QuartzSimpleSchedule extends QuartzSchedule {
  /* Repeat interval regardless of scale */
  def interval: Int
  /* Repeat behavior.
   * Left(Boolean) is 'forever' or 'not forever', which is obviated by
   * Right(Int) of '# of times to repeat'
   */
  def repeat: Either[Boolean, Int]

  protected def _build: SimpleScheduleBuilder = repeat match {
    case Right(count) => SimpleScheduleBuilder.simpleSchedule.withRepeatCount(count)
    case Left(forever) => if (forever)
      SimpleScheduleBuilder.simpleSchedule.repeatForever
    else
      SimpleScheduleBuilder.simpleSchedule.repeatForever
  }
}

final class QuartzSimpleHourlySchedule(val name: String,
                                       val interval: Int,
                                       val repeat: Either[Boolean, Int],
                                       val calendars: Seq[String]) extends QuartzSimpleSchedule {
  val schedule = _build.withIntervalInHours(interval).build()
}

final class QuartzSimpleMinutelySchedule(val name: String,
                                         val interval: Int,
                                         val repeat: Either[Boolean, Int],
                                         val calendars: Seq[String]) extends QuartzSimpleSchedule {
  val schedule = _build.withIntervalInMinutes(interval).build()
}

final class QuartzSimpleSecondlySchedule(val name: String,
                                         val interval: Int,
                                         val repeat: Either[Boolean, Int],
                                         val calendars: Seq[String]) extends QuartzSimpleSchedule {
  val schedule = _build.withIntervalInSeconds(interval).build()
}

final class QuartzSimpleMillisecondlySchedule(val name: String,
                                              val interval: Int,
                                              val repeat: Either[Boolean, Int],
                                              val calendars: Seq[String]) extends QuartzSimpleSchedule {
  val schedule = _build.withIntervalInMilliseconds(interval).build()
}

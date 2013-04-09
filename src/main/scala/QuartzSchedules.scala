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
  // type (Simple, Cron)
  // timezone (parseable) [optional, defaults to UTC] TODO: Default Timezone at toplevel
  // calendars = list of calendar names that "modify" this schedule
  // description = an optional description of the job [string] [optional]
  // TODO - Misfire Handling

  /* simple
   *
   * A "simple" schedule with some basic attributes similar to the default akka scheduler
   */
  // repeat block {
  //    scale = HOURS |  MINUTES | SECONDS | MILLISECONDS
  //    interval = INT
  //    count = INT [if not defined, sets repeats forever] [optional]
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

    val desc = catchMissing opt {
      config.getString("description")
    }

    catchMissing either { config.getString("type") } match {
      case Left(_) => throw new IllegalArgumentException("Schedule Type must be defined for " + name)
      case Right(typ) => typ.toUpperCase match {
        case "CRON" => parseCronSchedule(name, desc, config)(timezone, calendars)
        case "SIMPLE" => parseSimpleSchedule(name, desc, config)(calendars)
        case other =>
          throw new IllegalArgumentException("Unknown Quartz Schedule type '%s' for calendar '%s'. Valid types are Cron and Simple.".format(other, name))
      }
    }
  }

  def parseSimpleSchedule(name: String, desc: Option[String], config: Config)(calendars: Seq[String]): QuartzSimpleSchedule = {

    val interval = catchMissing or catchWrongType either { config.getInt("repeat.interval") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'repeat.interval' for Simple Schedule '%s'".format(name) +
          " – You must provide an Integer representing the time interval", t)
      case Right(i) => i
    }

    val repeat: Option[Int] = catchMissing opt { config.getInt("repeat.count") }

    catchMissing or catchWrongType either { config.getString("repeat.scale") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'repeat.scale' for Simple Schedule '%s'".format(name) +
                                           " – You must provide a scale of either Hours, Minutes, Seconds, or Milliseconds.", t)
      case Right(scale) => scale.toUpperCase match {
        case "HOURS" => new QuartzSimpleHourlySchedule(name, desc, interval, repeat, calendars)
        case "MINUTES" => new QuartzSimpleMinutelySchedule(name, desc, interval, repeat, calendars)
        case "SECONDS" => new QuartzSimpleSecondlySchedule(name, desc, interval, repeat, calendars)
        case "MILLISECONDS" => new QuartzSimpleMillisecondlySchedule(name, desc, interval, repeat, calendars)
        case other =>
          throw new IllegalArgumentException("Invalid 'repeat.scale' type for Simple Schedule '%s'".format(name) +
                                             " – Valid scales are Hours, Minutes, Seconds, Milliseconds.")
      }
    }
  }

  def parseCronSchedule(name: String, desc: Option[String], config: Config)(tz: TimeZone, calendars: Seq[String]): QuartzCronSchedule = {
    val expression = catchMissing or catchWrongType either { config.getString("expression") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'expression' for Cron Schedule '%s'. You must provide a valid Quartz CronExpression.".format(name), t)
      case Right(str) => catchParseErr either new CronExpression(str) match {
        case Left(t) =>
          throw new IllegalArgumentException("Invalid 'expression' for Cron Schedule '%s'. Failed to validate CronExpression.".format(name), t)
        case Right(expr) => expr
      }
    }
    new QuartzCronSchedule(name, desc, expression, tz, calendars)
  }
}

sealed trait QuartzSchedule {
  type T <: Trigger

  def name: String

  def description: Option[String]

  // todo - I don't like this as we can't guarantee the builder's state, but the Quartz API forces our hand
  def schedule: ScheduleBuilder[T]

  def calendars: Seq[String] // calendars that modify this schedule

  /**
   * Utility method that builds a trigger
   * with the data this schedule contains, given a name
   * Job association can happen separately at schedule time.
   */
  def buildTrigger(name: String): T = {
    @tailrec
    def addCalendars(_t: TriggerBuilder[T], _cals: Seq[String]): TriggerBuilder[T] = {
      if (_cals.length == 1)
        _t.modifiedByCalendar(_cals.head)
      else
        addCalendars(_t.modifiedByCalendar(_cals.head), _cals.tail)
    }

    val tB = TriggerBuilder.newTrigger()
                           .withIdentity(name + "_Trigger")
                           .withDescription(description.getOrElse(null))
                           .startNow()
                           .withSchedule(schedule)

    addCalendars(tB, calendars).build()
  }

}

final class QuartzCronSchedule(val name: String,
                               val description: Option[String],
                               val expression: CronExpression,
                               val timezone: TimeZone,
                               val calendars: Seq[String]) extends QuartzSchedule {

  type T = CronTrigger

  // Do *NOT* build, we need the uncompleted builder. I hate the Quartz API, truly.
  val schedule = CronScheduleBuilder.cronSchedule(expression).inTimeZone(timezone)
}

sealed abstract class QuartzSimpleSchedule extends QuartzSchedule {

  type T = SimpleTrigger

  /* Repeat interval regardless of scale */
  def interval: Int
  /* Repeat count
   * If none, repeat forever, else
   * Some(Int) of '# of times to repeat'
   *  validation of what the int's value means is deferred to Quartz
   */
  def repeat: Option[Int]

  protected def _build: SimpleScheduleBuilder = repeat match {
    case Some(count) => SimpleScheduleBuilder.simpleSchedule.withRepeatCount(count)
    case None =>  SimpleScheduleBuilder.simpleSchedule.repeatForever
  }
}

final class QuartzSimpleHourlySchedule(val name: String,
                                       val description: Option[String],
                                       val interval: Int,
                                       val repeat: Option[Int],
                                       val calendars: Seq[String]) extends QuartzSimpleSchedule {
  // Do *NOT* build, we need the uncompleted builder. I hate the Quartz API, truly.
  val schedule = _build.withIntervalInHours(interval)
}

final class QuartzSimpleMinutelySchedule(val name: String,
                                         val description: Option[String],
                                         val interval: Int,
                                         val repeat: Option[Int],
                                         val calendars: Seq[String]) extends QuartzSimpleSchedule {
  // Do *NOT* build, we need the uncompleted builder. I hate the Quartz API, truly.
  val schedule = _build.withIntervalInMinutes(interval)
}

final class QuartzSimpleSecondlySchedule(val name: String,
                                         val description: Option[String],
                                         val interval: Int,
                                         val repeat: Option[Int],
                                         val calendars: Seq[String]) extends QuartzSimpleSchedule {
  // Do *NOT* build, we need the uncompleted builder. I hate the Quartz API, truly.
  val schedule = _build.withIntervalInSeconds(interval)
}

final class QuartzSimpleMillisecondlySchedule(val name: String,
                                              val description: Option[String],
                                              val interval: Int,
                                              val repeat: Option[Int],
                                              val calendars: Seq[String]) extends QuartzSimpleSchedule {

  // Do *NOT* build, we need the uncompleted builder. I hate the Quartz API, truly.
  val schedule = _build.withIntervalInMilliseconds(interval)
}

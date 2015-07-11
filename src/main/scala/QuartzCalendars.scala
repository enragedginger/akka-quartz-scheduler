package com.typesafe.akka.extension.quartz

import com.typesafe.config.{ConfigObject, ConfigException, Config}
import java.util.TimeZone
import scala.util.control.Exception._
import org.quartz.{CronExpression, Calendar}
import org.quartz.impl.calendar._

import scala.collection.JavaConverters._
import java.text.{ParseException, SimpleDateFormat}
import collection.immutable

/**
 * Utility classes around the creation and configuration of Quartz Calendars.
 * All dates must be ISO-8601 compliant.
 */
object QuartzCalendars {
  // TODO - Support a default / "Base" calendar.
  // type (of enum list)
  // timezone (parseable) [optional, defaults to UTC]
  // description (string)

  /* annual
   *   excludes a set of days of the year
   *   e.g. bank holidays which are on same date every year
   *   doesn't take year into account, but can't calculate moveable feasts.
   *   i.e. you can specify "christmas" (always december 25) and it will get every christmas,
   *   but not "Easter" (which is calculated based on the first full moon on or after the spring equinox)
   */
  // excludeDates (list of MM-DD dates)

  /* holiday
   *  excludes full specified day, with the year taken into account
   */
  // excludeDates (list of ISO-8601 dates, YYYY-MM-DD)

  /* daily
   *  excludes a specified time range each day. cannot cross daily boundaries.
   *  only one time range PER CALENDAR.
   */
  // exclude block {
  //    startTime = <TIME>
  //    endTime   = <TIME>
  // }
  // time in the format "HH:MM[:SS[:mmm]]" where:
  //    HH is the hour of the specified time. The hour should be specified using military (24-hour) time and must be in the range 0 to 23.
  //    MM is the minute of the specified time and must be in the range 0 to 59.
  //    SS is the second of the specified time and must be in the range 0 to 59.
  //    mmm is the millisecond of the specified time and must be in the range 0 to 999.
  //  items enclosed in brackets ('[', ']') are optional.
  // !! The time range starting time must be before the time range ending time.
  //    Note this means that a time range may not cross daily boundaries (10PM - 2AM) !!


  /* monthly
   *  excludes a set of days of the month.
   */
  // excludeDays (list of ints from 1-31)

  /* weekly
   *  Excludes a set of days of the week, by default excludes Saturday and Sunday
   */
  // excludeDays (list of ints from 1-7 where 1 is sunday, 7 is saturday)
  // excludeWeekends (boolean) By default TRUE, *overriden by excludeDays* (e.g. if you say this is true but exclude sunday, exclude wins)

  /* cron
   *  excludes the set of times expressed by a given [Quartz CronExpression](http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html)
   *  Gets *one* expression set on it.
   *
   */
  // excludeExpression (Valid Quartz CronExpression)

  //  def parseCalendars(config)
  val catchMissing = catching(classOf[ConfigException.Missing])
  val catchWrongType = catching(classOf[ConfigException.WrongType])
  val catchParseErr = catching(classOf[ParseException])

  def apply(config: Config, defaultTimezone: TimeZone): immutable.Map[String, Calendar] = catchMissing opt {
    /** the extra toMap call is because the asScala gives us a mutable map... */
    config.getConfig("calendars").root.asScala.toMap.flatMap {
      case (key, value: ConfigObject) =>
        Some(key -> parseCalendar(key, value.toConfig, defaultTimezone))
      case _ =>
        None
    }
  } getOrElse immutable.Map.empty[String, Calendar]


  def parseFmt(raw: String, fmt: SimpleDateFormat)(tz: TimeZone): java.util.Calendar = {
    val c = java.util.Calendar.getInstance(tz)
    c.setTime(fmt.parse(raw))
    c
  }

  def parseAnnualCalendar(name: String, config: Config)(tz: TimeZone): AnnualCalendar = {
    val dateFmt = new SimpleDateFormat("MM-dd")
    dateFmt.setTimeZone(tz) // DateFormatters need a timezone to work properly
    val excludeDates = catchMissing or catchWrongType either { config.getStringList("excludeDates") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'excludeDates' for Annual calendar '%s'. You must provide a list of ISO-8601 compliant dates ('YYYY-MM-DD').".format(name), t)
      case Right(dates) => dates.asScala.map { d =>
        catchParseErr either {
          parseFmt(d, dateFmt)(tz)
        } match {
          case Left(t) =>
            throw new IllegalArgumentException("Invalid date '%s' in Annual Calendar '%s' - 'excludeDates'. You must provide an ISO-8601 compliant date ('YYYY-MM-DD').".format(d, name), t)
          case Right(dt) => dt
        }
      }
    }
    val cal = new AnnualCalendar()
    cal.setDaysExcluded(new java.util.ArrayList(excludeDates.asJava))
    cal
  }

  def parseHolidayCalendar(name: String, config: Config)(tz: TimeZone): HolidayCalendar = {
    val dateFmt = new SimpleDateFormat("yyyy-MM-dd")
    dateFmt.setTimeZone(tz) // DateFormatters need a timezone to work properly
    val excludeDates = catchMissing or catchWrongType either { config.getStringList("excludeDates") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'excludeDates' for Holiday Calendar '%s'. You must provide a list of ISO-8601 compliant dates ('YYYY-MM-DD').".format(name), t)
      case Right(dates) => dates.asScala.map { d =>
        catchParseErr either {
          parseFmt(d, dateFmt)(tz).getTime
        } match {
          case Left(t) =>
            throw new IllegalArgumentException("Invalid date '%s' in Holiday Calendar '%s' - 'excludeDates'. You must provide an ISO-8601 compliant date ('YYYY-MM-DD').".format(d, name), t)
          case Right(dt) => dt
        }
      }
    }
    val cal = new HolidayCalendar()
    excludeDates.foreach {  cal.addExcludedDate }
    cal
  }


  def parseDailyCalendar(name: String, config: Config): DailyCalendar = {
    def parseTimeEntry(entry: String) = catchMissing or catchWrongType either { config.getString(entry) } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry '%s' for Daily Calendar '%s'. You must provide a time in the format 'HH:MM[:SS[:mmm]]'".format(entry, name), t)
      case Right(rawTime) =>
        rawTime
    }


    val startTime = parseTimeEntry("exclude.startTime")
    val endTime = parseTimeEntry("exclude.endTime")

    // todo - verify invariant condition re: crossing daily boundaries( does quartz do that for us )?
    new DailyCalendar(startTime, endTime)
  }


  def parseWeeklyCalendar(name: String, config: Config): WeeklyCalendar = {
    val excludeDays = catchMissing or catchWrongType either { config.getIntList("excludeDays") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'excludeDays' for Weekly Calendar '%s'. You must provide a list of Integers between 1 and 7.".format(name), t)
      case Right(days) =>
        days.asScala
    }

    val excludeWeekends = catchMissing opt { config.getBoolean("excludeWeekends") } getOrElse true

    require(excludeDays.forall( d => d <= 7 && d >= 1),
            "Weekly Calendar '%s' - 'excludeDays' must consist of a list of Integers between 1 and 7".format(name))

    val cal = new WeeklyCalendar
    excludeDays.foreach { cal.setDayExcluded(_, true) }
    if (!excludeWeekends) {
      if (excludeDays.contains(7) || excludeDays.contains(1))
        throw new IllegalArgumentException("Weekly Calendar '%s' - Cannot set 'excludeWeekends' to false when you have explicitly excluded Saturday (7) or Sunday (1)".format(name))
      else {
        cal.setDayExcluded(1, false)
        cal.setDayExcluded(7, false)
      }
    }
    cal
  }

  def parseMonthlyCalendar(name: String, config: Config): MonthlyCalendar = {
    val excludeDays = catchMissing or catchWrongType either { config.getIntList("excludeDays") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'excludeDays' for Monthly Calendar '%s'. You must provide a list of Integers between 1 and 31.".format(name), t)
      case Right(days) =>
        days.asScala
    }
    require(excludeDays.forall( d => d <= 31 && d >= 1),
      "Monthly Calendar '%s' - 'excludeDays' must consist of a list of Integers between 1 and 31".format(name))
    val cal = new MonthlyCalendar
    excludeDays.foreach {  cal.setDayExcluded(_, true) }
    cal
  }

  def parseCronCalendar(name: String, config: Config): CronCalendar = {
    val exclude = catchMissing or catchWrongType either { config.getString("excludeExpression") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'excludeExpression' for Cron Calendar '%s'. You must provide a valid Quartz CronExpression.".format(name), t)
      case Right(expr) => catchParseErr either CronExpression.validateExpression(expr) match {
        case Left(t) =>
          throw new IllegalArgumentException("Invalid 'excludeExpression' for Cron Calendar '%s'. Failed to validate CronExpression.".format(name), t)
        case Right(_) => expr
      }
    }
    val cal = new CronCalendar(exclude)
    cal
  }

  def parseCalendar(name: String, config: Config, defaultTimezone: TimeZone): Calendar = {
    // parse common attributes
    val timezone = catchMissing opt {
      TimeZone.getTimeZone(config.getString("timezone")) // todo - this is bad, as Java silently swaps the timezone if it doesn't match...
    } getOrElse defaultTimezone

    val description = catchMissing opt {
      config.getString("description")
    }

    /// todo - make this whole thing a pattern extractor?
    catchMissing either config.getString("type") match {
      case Left(_) => throw new IllegalArgumentException("Calendar Type must be defined for " + name)
      case Right(typ) =>
        val cal = typ.toUpperCase match {
          case "ANNUAL" => parseAnnualCalendar(name, config)(timezone)
          case "HOLIDAY" => parseHolidayCalendar(name, config)(timezone)
          case "DAILY" => parseDailyCalendar(name, config)
          case "MONTHLY" => parseMonthlyCalendar(name, config)
          case "WEEKLY" => parseWeeklyCalendar(name, config)
          case "CRON" => parseCronCalendar(name, config)
          case other =>
              throw new IllegalArgumentException("Unknown Quartz Calendar type '%s' for calendar '%s'. Valid types are Annual, Holiday, Daily, Monthly, Weekly, and Cron.".format(other, name))
        }
        description.foreach{cal.setDescription}
        cal.setTimeZone(timezone)
        cal
    }
  }
}

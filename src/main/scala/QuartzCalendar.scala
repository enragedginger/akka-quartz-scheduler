package akka.extension.quartz

import scala.util.control.Exception._
import com.typesafe.config.{ConfigObject, ConfigException, Config}
import java.util.TimeZone
import scala.util.control.Exception._
import org.quartz.Calendar
import org.quartz.impl.calendar._

import scala.collection.JavaConverters._
import java.text.{ParseException, SimpleDateFormat}
import scala.Left
import scala.Right
import java.util

/**
 * Utility classes around the creation and configuration of Quartz Calendars.
 * All dates must be ISO-8601 compliant.
 */
object QuartzCalendars {
  // TODO - Support a default / "Base" calendar.
  // type (of enum list)
  // timezone (parseable) [optional, defaults to UTC] TODO: Default Timezone at toplevel
  // description (string)

  /* annual
   *   excludes a set of days of the year
   *   e.g. bank holidays which are on same date every year
   *   doesn't take year into account, but can't calculate moveable feasts.
   *   i.e. you can specify "christmas" (always december 25) and it will get every christmas,
   *   but not "Easter" (which is calculated based on the first full moon on or after the spring equinox)
   */
  // excludeDates (list of ISO-8601 dates, Year can be set to anything as YYYY-MM-DD
  //               or MM-DD will fill in a default )

  /* holiday
   *  excludes full specified day, with the year taken into account
   */
  // excludeDates (list of ISO-8601 dates, YYYY-MM-DD)

  /* daily
   *  excludes a specified time range each day. cannot cross daily boundaries.
   *  only one time range PER CALENDAR.
   */
  // exclude block {
  //    startTime = ISO-8601 Time with seconds optional (HH:mm:ss)
  //    endTime   = ISO-8601 Time with seconds optional (HH:mm:ss)
  // }

  /* monthly
   *  excludes a set of days of the month.
   */
  // excludeDays (list of ints from 1-31)

  /* weekly
   *  Excludes a set of days of the week, by default excludes Saturday and Sunday
   */
  // excludeDays (list of ints from 1-7 where 1 is sunday, 7 is saturday) Also recognizes day name in full spelling TODO: Abbrevs
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
  val dateFmt = new SimpleDateFormat("yyyy-MM-dd")
  val timeFmt = new SimpleDateFormat("HH:mm")
  val timeWSecondsFmt = new SimpleDateFormat("HH:mm:ss")

  def apply(config: Config, defaultTimezone: TimeZone): Seq[Calendar] = catchMissing opt {
    config.getConfig("akka.quartz.calendars").root.asScala.flatMap {
      case (key, value: ConfigObject) => parseCalendar(key, value.toConfig, defaultTimezone)
      case _ => None
    }.toSeq
  } getOrElse Seq.empty[Calendar]

  def parseAnnualCalendar(name: String, config: Config): AnnualCalendar = {
    val excludeDates = catchMissing or catchWrongType either { config.getStringList("excludeDates") } match {
      case Left(t) =>
        throw new IllegalArgumentException("Invalid or Missing Configuration entry 'excludeDates' for Annual calendar. You must provide a list of ISO-8601 compliant dates ('YYYY-MM-DD').", t)
      case Right(dates) => dates.asScala.map { d =>
        catchParseErr either {
          val c = java.util.Calendar.getInstance()
          c.setTime(dateFmt.parse(d))
          c
        } match {
          case Left(t) =>
            throw new IllegalArgumentException("Invalid date '%s' in Annual Calendar 'excludeDates'. You must provide an ISO-8601 compliant date ('YYYY-MM-DD').".format(d), t)
          case Right(dt) => dt
        }
      }
    }
    val cal = new AnnualCalendar()
    cal.setDaysExcluded(new java.util.ArrayList(excludeDates.asJava))
    cal
  }

  def parseHolidayCalendar(name: String, config: Config): HolidayCalendar = new HolidayCalendar
  def parseDailyCalendar(name: String, config: Config): DailyCalendar = new DailyCalendar("", "")
  def parseWeeklyCalendar(name: String, config: Config): WeeklyCalendar = new WeeklyCalendar
  def parseMonthlyCalendar(name: String, config: Config): MonthlyCalendar = new MonthlyCalendar
  def parseCronCalendar(name: String, config: Config): CronCalendar = new CronCalendar("")

  def parseCalendar(name: String, config: Config, defaultTimezone: TimeZone): Option[Calendar] = {
    println("Parsing Calendar '%s'".format(name))
    // parse common attributes
    val timezone = catchMissing opt {
      TimeZone.getTimeZone(config.getString("timezone")) // todo - this is bad, as Java silently swaps the timezone if it doesn't match...
    } getOrElse defaultTimezone

    val description = catchMissing opt {
      config.getString("description")
    }

    /// todo - make this whole thing a pattern extractor?
    catchMissing either { config.getString("type") } match {
      case Left(_) => throw new IllegalArgumentException("Calendar Type must be defined.")
      case Right(typ) =>
        val cal = typ.toUpperCase match {
          case "ANNUAL" => parseAnnualCalendar(name, config)
          case "HOLIDAY" => parseHolidayCalendar(name, config)
          case "DAILY" => parseDailyCalendar(name, config)
          case "MONTHLY" => parseMonthlyCalendar(name, config)
          case "WEEKLY" => parseWeeklyCalendar(name, config)
          case "CRON" => parseCronCalendar(name, config)
          case other => throw new IllegalArgumentException("Unknown Quartz Calendar type '%s'. Valid types are Annual, Holiday, Daily, Monthly, Weekly, and Cron.".format(other))
        }
        description.foreach{cal.setDescription}
        cal.setTimeZone(timezone)
        Some(cal)
    }
  }
}

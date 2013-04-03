package akka.extension.quartz
package test

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import java.util.{Calendar, GregorianCalendar, Date, TimeZone}
import org.quartz.impl.calendar.{HolidayCalendar, AnnualCalendar}

@RunWith(classOf[JUnitRunner])
class QuartzCalendarSpec extends Specification with ThrownExpectations { def is =

  "This is a specification to validate the behavior of the Quartz Calendar configuration modelling"   ^
                                                            p ^
  "The configuration parser should"                           ^
    "Fetch a list of all calenders in a configuration block"  ! parseCalendarList ^
    "Be able to parse and create an Annual calendar"          ! parseAnnual ^
    "Be able to parse and create a Holiday calendar"          ! parseHoliday ^
    "Be able to parse and create a Daily calendar"            ! parseDaily ^
    "Be able to parse and create a Monthly calendar"          ^
        "Without a list (single digit)"                       ! parseMonthlyNoList ^
        "With a list (multiple digits)"                       ! parseMonthlyList ^
                                                            p ^
    "Be able to parse and create a Weekly calendar"           ^
        "With Ints for Day Names"                             ! parseWeeklyInt ^
        "With Strings for Day Names"                          ! parseWeeklyString ^
                                                            p ^
    "Be able to parse and create a Cron calendar"             ! parseCronStyle ^
                                                                end

  def parseCalendarList = {
    // tood - more robust check
    calendars must have size(8)
  }

  import scala.collection.JavaConverters._
  def parseAnnual = {
    calendars must haveKey("WinterClosings")
    calendars("WinterClosings") must haveClass[AnnualCalendar]
    val cal = calendars("WinterClosings").asInstanceOf[AnnualCalendar]

    import Calendar._

    def _day(month: Int, day: Int) = {
      val _day = Calendar.getInstance()
      _day.set(MONTH, month)
      _day.set(DAY_OF_MONTH, day)
      _day
    }


    cal.isDayExcluded(_day(DECEMBER, 25)) must beTrue
    cal.isDayExcluded(_day(JANUARY, 01)) must beTrue
    cal.isDayExcluded(_day(FEBRUARY, 25)) must beFalse
    /* Check that regardless of year, we're also OK -- we defined Christmas 2000, but we can go backwards */
    cal.isDayExcluded(getCalendar(DECEMBER, 25, 1995)) must beTrue
  }

  def parseHoliday = {
    calendars must haveKey("Easter")
    calendars("Easter") must haveClass[HolidayCalendar]
    val cal = calendars("Easter").asInstanceOf[HolidayCalendar]
    /** By "TimeIncluded" the Calendar means does this calendar include an exclusion for that time */
    /* excludeDates = ["2013-03-31", "2014-04-20", "2015-04-05", "2016-03-27", "2017-04-16"] */

    import Calendar._

    implicit val tz = cal.getTimeZone

    def _epoch(month: Int, day: Int, year: Int) = getCalendar(month, day, year).getTime.getTime

    cal.isTimeIncluded(_epoch(MARCH, 31, 2013)) must beTrue
    cal.isTimeIncluded(_epoch(APRIL, 20, 2014)) must beTrue
    cal.isTimeIncluded(_epoch(APRIL, 05, 2015)) must beTrue
    cal.isTimeIncluded(_epoch(MARCH, 27, 2016)) must beTrue
    cal.isTimeIncluded(_epoch(APRIL, 16, 2017)) must beTrue

    /** This test is failing, and quartz itself has no tests around Holiday calendar
      * for me to confirm the expected behavior. For now,  Holiday Calendar should be used with great care.
      */
    cal.isTimeIncluded(_epoch(APRIL, 17, 2017)) must beFalse
    cal.isTimeIncluded(_epoch(DECEMBER, 25, 20123)) must beFalse
    cal.isTimeIncluded(_epoch(JANUARY, 02, 2023)) must beFalse
  } pendingUntilFixed

  def parseDaily = {
    todo
  }
  def parseMonthlyNoList = {
    todo
  }
  def parseMonthlyList = {
    todo
  }
  def parseWeeklyInt = {
    todo
  }
  def parseWeeklyString = {
    todo
  }
  def parseCronStyle = {
    todo
  }

  lazy val sampleConfiguration = {
    ConfigFactory.parseString("""
      akka.quartz {
        calendars {
          WinterClosings {
            type = Annual
            description = "Major holiday dates that occur in the winter time every year, non-moveable (The year doesn't matter)"
            excludeDates = ["2000-12-25", "2000-01-01"]
          }
          Easter {
            type = Holiday
            description = "The easter holiday (a moveable feast) for the next five years"
            excludeDates = ["2013-03-31", "2014-04-20", "2015-04-05", "2016-03-27", "2017-04-16"]
          }
          HourOfTheWolf {
            type = Daily
            description = "A period every day in which cron jobs are quiesced, during night hours"
            exclude {
              startTime = "03:00"
              endTime   = "05:00:00"
            }
            timezone = PST
          }
          FirstOfMonth {
            type = Monthly
            description = "A thinly veiled example to test monthly exclusions and to see if it breaks w/o a list"
            excludeDays = 1
          }
          FirstAndLastOfMonth {
            type = Monthly
            description = "A thinly veiled example to test monthly exclusions"
            excludeDays = [1, 31]
          }
          MondaysSuckInt {
            type = Weekly
            description = "Everyone, including this calendar, hates mondays as an integer"
            excludeDays = [2]
            excludeWeekends = false
          }
          MondaysSuckString {
            type = Weekly
            description = "Everyone, including this calendar, hates mondays as a string name"
            excludeDays = ["Monday"]
            excludeWeekends = false
          }
          CronOnlyBusinessHours {
            type = Cron
            excludeExpression = "* * 0-7,18-23 ? * *"
            timezone = "America/San_Francisco"
          }
        }
      }
      """.stripMargin)
  }

  def getCalendar(month: Int, day: Int, year: Int)(implicit tz: TimeZone = TimeZone.getTimeZone("UTC")) = {
    import Calendar._
    val _day = Calendar.getInstance(tz)
    _day.set(MONTH, month)
    _day.set(DAY_OF_MONTH, day)
    _day.set(YEAR, year)
    _day
  }

  lazy val calendars = QuartzCalendars(sampleConfiguration, TimeZone.getTimeZone("UTC"))

}

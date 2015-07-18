package com.typesafe.akka.extension.quartz
package test

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import java.util.{Calendar, GregorianCalendar, Date, TimeZone}
import org.quartz.impl.calendar._

@RunWith(classOf[JUnitRunner])
class QuartzCalendarSpec extends Specification with ThrownExpectations { def is =
  sequential ^
  "This is a specification to validate the behavior of the Quartz Calendar configuration modelling"   ^
                                                            p ^
  "The configuration parser should"                           ^
    "Fetch a list of all calendars in a configuration block"  ! parseCalendarList ^
    "Be able to parse and create an Annual calendar"          ! parseAnnual ^
    "Be able to parse and create a Holiday calendar"          ! parseHoliday ^
    "Be able to parse and create a Daily calendar"            ^
        "With a standard entry"                               ! parseDaily ^
                                                            p ^
    "Be able to parse and create a Monthly calendar"          ^
        "With just one day (a list, but single digit)"        ! parseMonthlyOneDay ^
        "With a list (multiple digits)"                       ! parseMonthlyList ^
                                                            p ^
    "Be able to parse and create a Weekly calendar"           ^
        "With Ints for Day Names"                             ! parseWeeklyInt ^
                                                            p ^
    "Be able to parse and create a Cron calendar"             ! parseCronStyle ^
                                                                end


  def parseCalendarList = {
    //TODO - more robust check
    calendars must have size(7)
  }

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
    cal.isDayExcluded(_day(JANUARY, 1)) must beTrue
    cal.isDayExcluded(_day(FEBRUARY, 25)) must beFalse
    /* Check that regardless of year, we're also OK -- we defined Christmas 2000, but we can go backwards or forwards */
    cal.isDayExcluded(getCalendar(DECEMBER, 25, 1995)) must beTrue
    cal.isDayExcluded(getCalendar(DECEMBER, 25, 1975)) must beTrue
    cal.isDayExcluded(getCalendar(DECEMBER, 25, 2075)) must beTrue
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
    cal.isTimeIncluded(_epoch(APRIL, 5, 2015)) must beTrue
    cal.isTimeIncluded(_epoch(MARCH, 27, 2016)) must beTrue
    cal.isTimeIncluded(_epoch(APRIL, 16, 2017)) must beTrue

    /** This test is failing, and quartz itself has no tests around Holiday calendar
      * for me to confirm the expected behavior. For now,  Holiday Calendar should be used with great care.
      */
    cal.isTimeIncluded(_epoch(APRIL, 17, 2017)) must beFalse
    cal.isTimeIncluded(_epoch(DECEMBER, 25, 20123)) must beFalse
    cal.isTimeIncluded(_epoch(JANUARY, 2, 2023)) must beFalse
  } pendingUntilFixed

  def parseDaily = {
    calendars must haveKey("HourOfTheWolf")
    calendars("HourOfTheWolf") must haveClass[DailyCalendar]
    val cal = calendars("HourOfTheWolf").asInstanceOf[DailyCalendar]

    import Calendar._
    implicit val tz = cal.getTimeZone

    // This is based on how quartz does its own testing. Not ideal.
    cal.toString must contain("'03:00:00:000 - 05:00:00:000', inverted: false")
  }

  def _monthDayRange(days: List[Int]) = (1 to 31) map { d => (days contains d) }

  // for some inexplicable reason WeeklyCalendar (not tested by quartz) includes day 0 also?!?!
  def _weekDayRange(days: List[Int]) = (0 to 7) map { d => (days contains d) }

  def parseMonthlyOneDay = {
    calendars must haveKey("FirstOfMonth")
    calendars("FirstOfMonth") must haveClass[MonthlyCalendar]
    val cal = calendars("FirstOfMonth").asInstanceOf[MonthlyCalendar]

    cal.getDaysExcluded.toList must containTheSameElementsAs(_monthDayRange(List(1)))
  }

  def parseMonthlyList = {
    calendars must haveKey("FirstAndLastOfMonth")
    calendars("FirstAndLastOfMonth") must haveClass[MonthlyCalendar]
    val cal = calendars("FirstAndLastOfMonth").asInstanceOf[MonthlyCalendar]

    cal.getDaysExcluded.toList must containTheSameElementsAs(_monthDayRange(List(1, 31)))
  }

  def parseWeeklyInt = {
    calendars must haveKey("MondaysSuck")
    calendars("MondaysSuck") must haveClass[WeeklyCalendar]
    val cal = calendars("MondaysSuck").asInstanceOf[WeeklyCalendar]

    cal.getDaysExcluded.toList must containTheSameElementsAs(_weekDayRange(List(2)))
  }

  def parseCronStyle = {
    calendars must haveKey("CronOnlyBusinessHours")
    calendars("CronOnlyBusinessHours") must haveClass[CronCalendar]
    val cal = calendars("CronOnlyBusinessHours").asInstanceOf[CronCalendar]

    // No real external testability of the Cron Calendar provided by quartz; rely on quartz working.
    cal.getCronExpression.toString must beEqualTo("* * 0-7,18-23 ? * *")
  }

  lazy val sampleConfiguration = {
    ConfigFactory.parseString("""
        calendars {
          WinterClosings {
            type = Annual
            description = "Major holiday dates that occur in the winter time every year, non-moveable (The year doesn't matter)"
            excludeDates = ["12-25", "01-01"]
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
            description = "A thinly veiled example to test monthly exclusions of one day"
            excludeDays = [1]
          }
          FirstAndLastOfMonth {
            type = Monthly
            description = "A thinly veiled example to test monthly exclusions"
            excludeDays = [1, 31]
          }
          MondaysSuck {
            type = Weekly
            description = "Everyone, including this calendar, hates mondays as an integer"
            excludeDays = [2]
            excludeWeekends = false
          }
          CronOnlyBusinessHours {
            type = Cron
            excludeExpression = "* * 0-7,18-23 ? * *"
            timezone = "America/San_Francisco"
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

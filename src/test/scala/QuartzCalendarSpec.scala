package com.typesafe.akka.extension.quartz

import java.time.LocalDate

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import com.typesafe.config.ConfigFactory
import java.util.{Calendar, Date, TimeZone}

import scala.collection.JavaConverters._
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

    cal.isDayExcluded(getCalendar(JANUARY,   1, 1995)) must beTrue
    cal.isDayExcluded(getCalendar(JANUARY,   1, 1975)) must beTrue
    cal.isDayExcluded(getCalendar(JANUARY,   1, 2075)) must beTrue

    cal.isDayExcluded(getCalendar(JANUARY,   2, 1995)) must beFalse
    cal.isDayExcluded(getCalendar(JANUARY,   2, 1975)) must beFalse
    cal.isDayExcluded(getCalendar(JANUARY,   2, 2075)) must beFalse

    cal.isDayExcluded(getCalendar(DECEMBER, 25, 1995)) must beTrue
    cal.isDayExcluded(getCalendar(DECEMBER, 25, 1975)) must beTrue
    cal.isDayExcluded(getCalendar(DECEMBER, 25, 2075)) must beTrue

    cal.isDayExcluded(getCalendar(DECEMBER, 31, 1995)) must beFalse
    cal.isDayExcluded(getCalendar(DECEMBER, 31, 1975)) must beFalse
    cal.isDayExcluded(getCalendar(DECEMBER, 31, 2075)) must beFalse
  }

 def parseHoliday = {
   calendars must haveKey("Easter")
   calendars("Easter") must haveClass[HolidayCalendar]

   calendars("Easter").asInstanceOf[HolidayCalendar].getExcludedDates.asScala must containAllOf(List(
     getDate(2013, 3, 31),
     getDate(2014, 4, 20),
     getDate(2015, 4,  5),
     getDate(2016, 3, 27),
     getDate(2017, 4, 16)
   ))

 }


  def parseDaily = {
    calendars must haveKey("HourOfTheWolf")
    calendars("HourOfTheWolf") must haveClass[DailyCalendar]
    val cal = calendars("HourOfTheWolf").asInstanceOf[DailyCalendar]

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
            description = "The Easter holiday (a moveable feast) for the next five years"
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

  def getCalendar(month: Int, day: Int, year: Int)(implicit tz: TimeZone = TimeZone.getTimeZone("UTC")): Calendar = {
    val _day = Calendar.getInstance(tz)
    _day.set(year, month, day)
    _day
  }

  def getDate(year: Int, month: Int, day: Int): Date = {
    Date.from(LocalDate.of(year, month, day).atStartOfDay(java.time.ZoneId.systemDefault).toInstant)
  }


  lazy val calendars = QuartzCalendars(sampleConfiguration, TimeZone.getDefault)

}

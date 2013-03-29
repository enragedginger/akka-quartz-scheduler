package akka.extension.quartz
package test

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import java.util.TimeZone

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
    val calendars = QuartzCalendars(sampleConfiguration, TimeZone.getTimeZone("UTC"))
    calendars must have size(8)

  }

  def parseAnnual = {
    todo
  }
  def parseHoliday = {
    todo
  }
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
            description = "Major holiday dates that occur in the winter time every year, non-moveable"
            excludeDates = ["2013-12-25", "2014-01-01"]
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


}

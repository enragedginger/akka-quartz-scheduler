package org.apache.pekko.extension.quartz

import com.typesafe.config.{Config, ConfigFactory}

object SchedulingFunctionalTest {

  val tickTolerance = 1000

  lazy val sampleConfiguration: Config = { ConfigFactory.parseString("""
    pekko {
      event-handlers = ["org.apache.pekko.testkit.TestEventListener"]
      loglevel = "INFO"
      quartz {
        defaultTimezone = "UTC"
        schedules {
          cronEvery30Seconds {
            description = "A cron job that fires off every 30 seconds"
            expression = "*/30 * * ? * *"
          }
          cronEvery15Seconds {
            description = "A cron job that fires off every 15 seconds"
            expression = "*/15 * * ? * *"
          }
          cronEvery12Seconds {
            description = "A cron job that fires off every 10 seconds"
            expression = "*/12 * * ? * *"
          }
          cronEvery10SecondsWithFireTime {
            description = "A cron job that fires off every 10 seconds with FireTime"
            expression = "*/10 * * ? * *"
          }
          cronEvery10Seconds {
            description = "A cron job that fires off every 10 seconds"
            expression = "*/10 * * ? * *"
          }
          cronEvery10SecondsWithFireTimes {
            description = "A cron job that fires off every 10 seconds with FireTimes"
            expression = "*/10 * * ? * *"
          }
          cronEvery10Seconds {
            description = "A cron job that fires off every 10 seconds"
            expression = "*/10 * * ? * *"
          }
          cronEvery5Seconds {
            description = "A cron job that fires off every 5 seconds"
            expression = "*/5 * * ? * *"
          }
          cronEveryEvenSecond {
            description = "A cron job that fires off every even second"
            expression = "0/2 * * ? * *"
          }
          cronEveryMidnight {
            description = "A cron job that fires off every Midnight"
            expression = "0 0 0 * * ?"
          }
        }
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
            timezone = "America/Los_Angeles"
          }
        }
      }
    }
                                                               """.stripMargin)
  }
}

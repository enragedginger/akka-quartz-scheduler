package akka.extension.quartz
package test

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import java.util.{Calendar, GregorianCalendar, Date, TimeZone}
import org.quartz.impl.calendar._
import org.quartz.impl.triggers.CronTriggerImpl
import org.quartz.TriggerUtils
import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class QuartzScheduleSpec extends Specification with ThrownExpectations { def is =
  sequential ^
  "This is a specification to validate the behavior of the Quartz Schedule configuration parser" ^
    "The configuration parser should"                            ^
      "Fetch a list of all schedules in the configuration block" ! parseScheduleList ^
      "Be able to parse out a cron schedule"                     ! parseCronSchedule ^
      "Be able to parse out a cron schedule w/ calendars"        ! parseCronScheduleCalendars ^
                                                                   end

  def parseScheduleList = {
    schedules must haveSize(2)
  }

  def parseCronSchedule = {
    schedules must haveKey("cronEvery10Seconds")
    schedules("cronEvery10Seconds") must haveClass[QuartzCronSchedule]
    val s = schedules("cronEvery10Seconds").asInstanceOf[QuartzCronSchedule]

    // build a trigger to test against
    val _t = s.buildTrigger("parseCronScheduleTest")

    _t must haveClass[CronTriggerImpl]

    val t = _t.asInstanceOf[CronTriggerImpl]

    val startHour = 3
    val endHour = 9
    val numExpectedFirings = (endHour - startHour) * 60 /* 60 minutes in an hour */ * 6 /* 6 ticks per minute */
    val firings = TriggerUtils.computeFireTimesBetween(t, null, getDate(startHour, 0), getDate(endHour, 0)).asScala

    firings must have size(numExpectedFirings)
  }

  def parseCronScheduleCalendars = {
    // relies on calendar parsing working...
    val calendars = QuartzCalendars(sampleCalendarConfig, TimeZone.getTimeZone("UTC"))
    // excludes anything not during business hours - 8 - 5
    val bizHoursCal = calendars("CronOnlyBusinessHours")

    schedules must haveKey("cronEvery30Seconds")
    schedules("cronEvery30Seconds") must haveClass[QuartzCronSchedule]
    val s = schedules("cronEvery30Seconds").asInstanceOf[QuartzCronSchedule]

    // build a trigger to test against
    val _t = s.buildTrigger("parseCronScheduleTest")

    _t must haveClass[CronTriggerImpl]

    val t = _t.asInstanceOf[CronTriggerImpl]

    val startHour = 3
    val endHour = 22
    // we don't follow the startHour and endHour because of business hours..
    // the cron exemption rul (taken from quartz docs) actually lets jobs run from 0800 - 1759 (doh)
    val numExpectedFirings = (18 - 8) * 60 /* 60 minutes in an hour */ * 2 /* 2 ticks per minute */
    val firings = TriggerUtils.computeFireTimesBetween(t, bizHoursCal, getDate(startHour, 0), getDate(endHour, 0)).asScala

    firings must have size(numExpectedFirings)
  }

  def getDate(hour: Int, minute: Int)(implicit tz: TimeZone = TimeZone.getTimeZone("UTC")) = {
    import Calendar._
    val _day = Calendar.getInstance(tz)
    _day.set(HOUR_OF_DAY, hour) // 24 hour ... HOUR is am/pm
    _day.set(MINUTE, minute)
    _day.getTime
  }

  lazy val schedules = QuartzSchedules(sampleConfiguration, TimeZone.getTimeZone("UTC"))

  lazy val sampleCalendarConfig = {
    ConfigFactory.parseString(
      """
      calendars {
        CronOnlyBusinessHours {
          type = Cron
          excludeExpression = "* * 0-7,18-23 ? * *"
          timezone = "America/San_Francisco"
        }
      }
      """.stripMargin)
  }

  lazy val sampleConfiguration = {
    ConfigFactory.parseString("""
      schedules {
        cronEvery30Seconds {
          type = Cron
          description = "A cron job that fires off every 30 seconds"
          expression = "*/30 * * ? * *"
          calendars = ["CronOnlyBusinessHours"]
        }
        cronEvery10Seconds {
          type = Cron
          description = "A cron job that fires off every 10 seconds"
          expression = "*/10 * * ? * *"
        }
      }
    """.stripMargin)
  }
}


package akka.extension.quartz
package test

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.testkit.{TestProbe, TestKit, ImplicitSender}
import akka.util.duration._
import akka.util.Duration

/**
 * Separate base trait needed to get the non-default constructor to initialize properly,
 * see http://brianmckenna.org/blog/akka_scalacheck_specs2  (or just try moving this out of a trait and back on
 * the class)
 */
trait AkkaTestConfig extends Specification
                        with ThrownExpectations
                        with ImplicitSender { self: TestKit => }

@RunWith(classOf[JUnitRunner])
class QuartzSchedulerFunctionalSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
                                                             with AkkaTestConfig {

  def this() = this(ActorSystem("QuartzSchedulerFunctionalSpec", SchedulingFunctionalTest.sampleConfiguration))


  def is =
  sequential /** parallel execution makes a mess when testing actorsystems */ ^
  "This is a specification to validate the behavior of the Quartz Scheduler in a functional manner" ^
                                                      p^
  "The Quartz Scheduling Extension should"             ^
      "Reject a job which is not named in the config"  ! rejectUnconfiguredJob ^
      "Properly setup & execute a Simple Job"          ! scheduleSimpleJob ^
      "Properly setup & execute a Cron Job"            ! scheduleCronJob   ^
                                                         end

  def rejectUnconfiguredJob = {
    val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
    val probe = TestProbe()
    receiver ! NewProbe(probe.ref)
    lazy val schedule = QuartzSchedulerExtension(_system).schedule("fooBarBazSpamEggsOMGPonies!", receiver, Tick)

    schedule must throwA[IllegalArgumentException]
  }

  def scheduleSimpleJob = {
    todo
  /*  val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
    val probe = TestProbe()
    receiver ! NewProbe(probe.ref)
    val jobDt = QuartzSchedulerExtension(_system).schedule("simpleEvery20Seconds", receiver, Tick)

    System.err.println("Job DateTime: " + jobDt)

    val receipt = probe.receiveN(3, Duration(1, "minutes"))

    System.err.println("Receipt: " + receipt)

    receipt must not beNull*/

  }

  def scheduleCronJob = {
    val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
    val probe = TestProbe()
    receiver ! NewProbe(probe.ref)
    val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery10Seconds", receiver, Tick)

    System.err.println("Job DateTime: " + jobDt)

    val receipt = probe.receiveN(9, Duration(1, "minutes"))

    System.err.println("Receipt: " + receipt)

    receipt must not beNull
  }

  case class NewProbe(probe: ActorRef)
  case object Tick
  case object Tock

  class ScheduleTestReceiver extends Actor with ActorLogging {
    var probe: ActorRef = _
    def receive = {
      case NewProbe(_p) =>
        probe = _p
      case Tick =>
        log.info("Got a Tick.")
        probe ! Tock
    }
  }



}

object SchedulingFunctionalTest {
    lazy val sampleConfiguration = { ConfigFactory.parseString("""
    akka {
      loglevel = "DEBUG"
      quartz {
        defaultTimezone = "UTC"
        schedules {
          simpleEvery20Seconds {
            type = Simple
            description = "A simple job that fires every minute"
            calendars = ["WinterClosings", "FirstOfMonth"]
            repeat {
              scale = Seconds
              interval = 20
            }
          }
          simpleEvery10Seconds10Times {
            type = Simple
            description = "A simple job that fires every 10 seconds and stops after 10 times"
            repeat {
              scale = Seconds
              interval = 10
              repeat = 10
            }
          }
          cronEvery30Seconds {
            type = Cron
            description = "A cron job that fires off every 30 seconds"
            expression = "*/30 * * ? * *"
          }
          cronEvery10Seconds {
            type = Cron
            description = "A cron job that fires off every 10 seconds"
            expression = "*/10 * * ? * *"
          }
        }
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
      }
    }
                                                               """.stripMargin)
  }
}

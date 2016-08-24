package com.typesafe.akka.extension.quartz
package test

import java.util.{Calendar, Date}

import akka.japi.Option.Some
import org.junit.runner.RunWith
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.testkit._
import scala.concurrent.duration._
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.MustMatchers


@RunWith(classOf[JUnitRunner])
class QuartzSchedulerFunctionalSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with ImplicitSender
  with WordSpecLike
  with MustMatchers with BeforeAndAfterAll {

  override def afterAll {
    system.shutdown()
    system.awaitTermination()
  }

  def this() = this(ActorSystem("QuartzSchedulerFunctionalSpec", SchedulingFunctionalTest.sampleConfiguration))

  "The Quartz Scheduling Extension"  must {
    "Reject a job which is not named in the config" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)


      an[IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).schedule("fooBarBazSpamEggsOMGPonies!", receiver, Tick)
      }

    }

    "Properly Setup & Execute a Cron Job" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery10Seconds", receiver, Tick)


      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(1, MINUTES), Duration(15, SECONDS), 5) {
        case Tock =>
          Tock
      }

      receipt must contain(Tock)
      receipt must have size(5)

    }

    "Properly Setup & Execute a Cron Job via Event Stream" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      _system.eventStream.subscribe(receiver, Tick.getClass)
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery12Seconds", _system.eventStream, Tick)


      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(1, MINUTES), Duration(15, SECONDS), 5) {
        case Tock =>
          Tock
      }

      receipt must contain(Tock)
      receipt must have size(5)

    }

    "Delayed Setup & Execute a Cron Job" in {
      val now = Calendar.getInstance()
      val t = now.getTimeInMillis()
      val after65s = new Date(t + (35 * 1000))

      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery15Seconds", receiver, Tick, Some(after65s))

      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(30, SECONDS), Duration(30, SECONDS), 2) {
        case Tock =>
          Tock
      }

      receipt must have size(0)

      /*
      Get the startDate and calculate the next run based on the startDate
      The schedule only runs on 0,15,30,45 each minute and will run at the first opportunity after the startDate
       */
      val scheduleCalender = Calendar.getInstance()
      val jobCalender = Calendar.getInstance()
      scheduleCalender.setTime(after65s)
      jobCalender.setTime(jobDt)

      val seconds = scheduleCalender.get(Calendar.SECOND)
      val addSeconds = 15 - (seconds % 15)
      val secs = if(addSeconds > 0) addSeconds else 15
      scheduleCalender.add(Calendar.SECOND, secs)

      //Dates must be equal in seconds
      Math.floor(jobCalender.getTimeInMillis / 1000).toLong mustEqual Math.floor(scheduleCalender.getTimeInMillis / 1000).toLong
    }

    "Properly Setup & Execute a Cron Job with ActorSelection as receiver" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery5Seconds", _system.actorSelection(receiver.path), Tick)


      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(1, MINUTES), Duration(15, SECONDS), 5) {
        case Tock =>
          Tock
      }


      receipt must contain(Tock)
      receipt must have size(5)

    }
  }

  "The Quartz Scheduling Extension with Dynamic Create" must {
    "Throw exception if creating schedule that already exists" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))

      an [IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).createSchedule("cronEvery10Seconds", None, "*/10 * * ? * *", None)
      }
    }

    "Throw exception if creating a schedule that has invalid cron expression" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))

      an [IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).createSchedule("nonExistingCron", None, "*/10 x * ? * *", None)
      }
    }

    "Add new, schedulable schedule with valid inputs" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)

      QuartzSchedulerExtension(_system).createSchedule("nonExistingCron", Some("Creating new dynamic schedule"), "*/1 * * ? * *", None)
      val jobDt = QuartzSchedulerExtension(_system).schedule("nonExistingCron", receiver, Tick)


      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(30, SECONDS), Duration(15, SECONDS), 5) {
        case Tock =>
          Tock
      }

      receipt must contain(Tock)
      receipt must have size(5)
    }
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
      event-handlers = ["akka.testkit.TestEventListener"]
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
          cronEvery10Seconds {
            description = "A cron job that fires off every 10 seconds"
            expression = "*/10 * * ? * *"
          }
          cronEvery5Seconds {
            description = "A cron job that fires off every 5 seconds"
            expression = "*/5 * * ? * *"
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
            timezone = "America/San_Francisco"
          }
        }
      }
    }
                                                               """.stripMargin)
  }
}

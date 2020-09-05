package com.typesafe.akka.extension.quartz

import java.util.{Calendar, Date}

import akka.japi.Option.Some
import org.junit.runner.RunWith
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.testkit._
import scala.concurrent._
import scala.concurrent.duration._
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.MustMatchers
import org.scalatest.Matchers


@RunWith(classOf[JUnitRunner])
class QuartzSchedulerFunctionalSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll {

  override def afterAll {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }

  def this() = this(ActorSystem("QuartzSchedulerFunctionalSpec", SchedulingFunctionalTest.sampleConfiguration))

  "The Quartz Scheduling Extension" must {

    val tickTolerance = 500 // 500 millisecond tolerance

    "Reject a job which is not named in the config" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)


      an[IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).schedule("fooBarBazSpamEggsOMGPonies!", receiver, Tick)
      }

    }

    "Properly Setup & Execute a Cron Job with correct fireTime" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery10SecondsWithFireTime", receiver, MessageRequireFireTime(Tick))

      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(1, MINUTES), Duration(15, SECONDS), 5) {
        case TockWithFireTime(scheduledFireTime) =>
          scheduledFireTime
      }
      0 until 5 foreach { i =>
        assert(receipt(i) === jobDt.getTime + i * 10 * 1000 +- tickTolerance)
      }
      receipt must have size (5)
    }

    "Properly Setup & Execute a Cron Job with correct fireTimes" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery10SecondsWithFireTimes", receiver, MessageRequireFireTime(Tick, AllFiringTimes))

      val receipt = probe.receiveWhile(Duration(1, MINUTES), Duration(15, SECONDS), 5) {
        case TockWithFireTimes(previousFireTime, scheduledFireTime, nextFireTime) =>
          (previousFireTime, scheduledFireTime, nextFireTime)
      }
      0 until 5 foreach { i =>
        val expectedCurrent = jobDt.getTime + i * 10 * 1000
        val expectedPrevious = if (i == 0) 0 else expectedCurrent - 10 * 1000
        val expectedNext = expectedCurrent + 10 * 1000
        assert(receipt(i)._1 === expectedPrevious +- tickTolerance)
        assert(receipt(i)._2 === expectedCurrent +- tickTolerance)
        assert(receipt(i)._3 === expectedNext +- tickTolerance)
      }
      receipt must have size (5)
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
      receipt must have size (5)

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
      receipt must have size (5)

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

      receipt must have size (0)

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
      val secs = if (addSeconds > 0) addSeconds else 15
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
      receipt must have size (5)
    }
  }

  "The Quartz Scheduling Extension with Reschedule" must {
    "Reschedule an existing Cron Job" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      QuartzSchedulerExtension(_system).schedule("cronEveryEvenSecond", receiver, Tick)

      noException should be thrownBy {
        val newDate = QuartzSchedulerExtension(_system).rescheduleJob("cronEveryEvenSecond", receiver, Tick, None, "0/59 * * ? * *")
        val jobCalender = Calendar.getInstance()
        jobCalender.setTime(newDate)
        jobCalender.get(Calendar.SECOND) mustEqual 59
      }
    }
  }

  "Get next trigger date by schedule name" in {
    val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
    val probe = TestProbe()
    receiver ! NewProbe(probe.ref)
    val jobDt = QuartzSchedulerExtension(_system).schedule("cronEveryMidnight", _system.actorSelection(receiver.path), Tick)
    val nextRun = QuartzSchedulerExtension(_system).nextTrigger("cronEveryMidnight")

    assert(nextRun.getOrElse(new java.util.Date()) == jobDt)
  }

  "The Quartz Scheduling Extension with Dynamic Create" must {
    "Throw exception if creating schedule that already exists" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))

      an[IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).createSchedule("cronEvery10Seconds", None, "*/10 * * ? * *", None)
      }
    }

    "Throw exception if creating a schedule that has invalid cron expression" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))

      an[IllegalArgumentException] must be thrownBy {
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
      receipt must have size (5)
    }
  }
  
  
  /**
   * JobSchedule operations {create, update, delete} combine existing 
   * QuartzSchedulerExtension {createSchedule, schedule, rescheduleJob} 
   * and adds deleleteJobSchedule (unscheduleJob synonym created for naming 
   * consistency with existing rescheduleJob method). 
   */
  "The Quartz Scheduling Extension with Dynamic create, update, delete JobSchedule operations" must {
    "Throw exception if creating job schedule that already exists" in {
      
      val alreadyExistingScheduleJobName = "cronEvery10Seconds"
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)      
      
      an [IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).createJobSchedule(alreadyExistingScheduleJobName, receiver, Tick, None, "*/10 * * ? * *", None)
      }
    }

    "Throw exception if creating a scheduled job with schedule that has invalid cron expression" in {
      
      // Remark: Tests are not completely in isolation as using "nonExistingCron" 
      // schedule name here would fail because of use and definition in former:
      // "Add new, schedulable schedule with valid inputs" test. 
      val nonExistingScheduleJobName = "nonExistingCron_2"
      
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))

      an [IllegalArgumentException] must be thrownBy {
        QuartzSchedulerExtension(_system).createJobSchedule("nonExistingCron_2", receiver, Tick, None, "*/10 x * ? * *", None)
      }
    }

    "Add new, schedulable job and schedule with valid inputs" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)

      val jobDt = QuartzSchedulerExtension(_system).createJobSchedule("nonExistingCron_2", receiver, Tick, Some("Creating new dynamic schedule"), "*/1 * * ? * *", None)

      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveWhile(Duration(30, SECONDS), Duration(15, SECONDS), 5) {
        case Tock =>
          Tock
      }

      receipt must contain(Tock)
      receipt must have size(5)
    }
    
    "Reschedule an existing job schedule Cron Job" in {
      
      val toRescheduleJobName = "toRescheduleCron_1"
      
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)

      val jobDt = QuartzSchedulerExtension(_system).createJobSchedule(toRescheduleJobName, receiver, Tick, Some("Creating new dynamic schedule for updateJobSchedule test"), "*/4 * * ? * *")      

      noException should be thrownBy {
        val newFirstTimeTriggerDate = QuartzSchedulerExtension(_system).updateJobSchedule(toRescheduleJobName, receiver, Tick, Some("Updating new dynamic schedule for updateJobSchedule test"), "42 * * ? * *")   
        val jobCalender = Calendar.getInstance()
        jobCalender.setTime(newFirstTimeTriggerDate)
        jobCalender.get(Calendar.SECOND) mustEqual 42
      }
    }    


    "Delete an existing job schedule Cron Job without any error and allow successful creation of new schedule with identical job name" in {
      
      val toDeleteSheduleJobName = "toBeDeletedscheduleCron_1"
      
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)

      val jobDt = QuartzSchedulerExtension(_system).createJobSchedule(toDeleteSheduleJobName, receiver, Tick, Some("Creating new dynamic schedule for deleteJobSchedule test"), "*/7 * * ? * *")      

      noException should be thrownBy {
        // Delete existing scheduled job
        val success = QuartzSchedulerExtension(_system).deleteJobSchedule(toDeleteSheduleJobName)
        if (success) {
        
        // Create a new schedule job reusing former toDeleteSheduleJobName. This will fail if delebeJobSchedule is not effective.
        val newJobDt = QuartzSchedulerExtension(_system).createJobSchedule(toDeleteSheduleJobName, receiver, Tick, Some("Creating new dynamic schedule after deleteJobSchedule success"), "8 * * ? * *")        
        val jobCalender = Calendar.getInstance()
        jobCalender.setTime(newJobDt)
        jobCalender.get(Calendar.SECOND) mustEqual 8
        } else {
          fail(s"deleteJobSchedule(${toDeleteSheduleJobName}) expected to return true returned false.")
        }
      }
    }
    
    "Delete a non existing job schedule Cron Job with no error and a return value false" in {
      
      val nonExistingCronToBeDeleted = "nonExistingCronToBeDeleted"
      
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)

      noException should be thrownBy {
        // Deleting non existing scheduled job 
        val success = QuartzSchedulerExtension(_system).deleteJobSchedule(nonExistingCronToBeDeleted)
        // must return false
        if (success) {
          fail(s"deleteJobSchedule(${nonExistingCronToBeDeleted}) expected to return false returned true.")
        } 
      }
    }    
    
    
       
  }  


  case class NewProbe(probe: ActorRef)
  case object Tick
  case object Tock
  case class TockWithFireTime(scheduledFireTime: Long)
  case class TockWithFireTimes(previousFireTime: Long, scheduledFireTime: Long, nextFireTime: Long)

  class ScheduleTestReceiver extends Actor with ActorLogging {
    var probe: ActorRef = _
    def receive = {
      case NewProbe(_p) =>
        probe = _p
      case Tick =>
        log.info(s"Got a Tick.")
        probe ! Tock
      case MessageWithFireTime(Tick, scheduledFireTime) =>
        log.info(s"Got a Tick for ${scheduledFireTime.getTime}.")
        probe ! TockWithFireTime(scheduledFireTime.getTime)
      case MessageWithFireTimes(Tick, previousFireTime, scheduledFireTime, nextFireTime) =>
        log.info(s"Got a Tick for previousFireTime=${previousFireTime} scheduledFireTime=${scheduledFireTime} nextFireTime=${nextFireTime}")
        probe ! TockWithFireTimes(
          previousFireTime.map(u => u.getTime).getOrElse(0),
          scheduledFireTime.map(u => u.getTime).getOrElse(0),
          nextFireTime.map(u => u.getTime).getOrElse(0))
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
          cronEvery10SecondsWithFireTime{
          description = "A cron job that fires off every 10 seconds with FireTime"
          expression = "*/10 * * ? * *"}
          cronEvery10Seconds {
            description = "A cron job that fires off every 10 seconds"
            expression = "*/10 * * ? * *"
          }
          cronEvery10SecondsWithFireTimes{
          description = "A cron job that fires off every 10 seconds with FireTimes"
          expression = "*/10 * * ? * *"}
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
            timezone = "America/San_Francisco"
          }
        }
      }
    }
                                                               """.stripMargin)
  }
}

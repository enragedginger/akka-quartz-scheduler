package com.typesafe.akka.extension.quartz

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.japi.Option.Some
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.specs2.runner.JUnitRunner

import java.util.logging.Logger
import java.util.{Calendar, Date}
import scala.concurrent._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class QuartzTypedSchedulerFunctionalSpec
  extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  private lazy val testKit = ActorTestKit(SchedulingFunctionalTest.sampleConfiguration)
  private val _system = testKit.internalSystem

  override protected def afterAll(): Unit = {
    this.testKit.shutdownTestKit()
    Await.result(this.testKit.internalSystem.whenTerminated, Duration.Inf)
  }

  "The Quartz Scheduling Extension" must {

    val tickTolerance = SchedulingFunctionalTest.tickTolerance

    "Reject a job which is not named in the config" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      an[IllegalArgumentException] must be thrownBy {
        QuartzSchedulerTypedExtension(_system).scheduleTyped("fooBarBazSpamEggsOMGPonies!", receiver, Tick)
      }

    }

    "Properly Setup & Execute a Cron Job with correct fireTime" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)
      val extension = QuartzSchedulerTypedExtension(_system)
      val jobDt = extension.scheduleTyped("cronEvery10SecondsWithFireTime", receiver, MessageRequireFireTime(Tick))

      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt: Seq[AnyRef] = probe.receiveMessages(5, Duration(50, SECONDS))
      0 until 5 foreach { i =>
        val obj = receipt(i) match {
          case TockWithFireTime(scheduledFireTime, _, _) =>
            scheduledFireTime
        }
        assert(obj === jobDt.getTime + i * 10 * 1000 +- tickTolerance)
      }

      receipt must have size (5)
      extension.cancelJob("cronEvery10SecondsWithFireTime")
    }

    "Properly Setup & Execute a Cron Job with correct fireTimes" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)
      val extension = QuartzSchedulerTypedExtension(_system)
      val jobDt = extension.scheduleTyped("cronEvery10SecondsWithFireTimes", receiver, MessageRequireFireTime(Tick))

      val receipt = probe.receiveMessages(5, Duration(1, MINUTES))
      0 until 5 foreach { i =>
        val obj = receipt(i) match {
          case TockWithFireTime(scheduledFireTime, previousFireTime, nextFireTime) =>
            (scheduledFireTime, previousFireTime, nextFireTime)
        }

        val expectedCurrent = jobDt.getTime + i * 10 * 1000
        val expectedPrevious = if (i == 0) 0 else expectedCurrent - 10 * 1000
        val expectedNext = expectedCurrent + 10 * 1000
        assert(obj._1 === expectedCurrent +- tickTolerance)
        assert(obj._2.getOrElse(0L) === expectedPrevious +- tickTolerance)
        assert(obj._3.get === expectedNext +- tickTolerance)
      }

      receipt must have size (5)
      extension.cancelJob("cronEvery10SecondsWithFireTime")
    }

    "Properly Setup & Execute a Cron Job" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)
      val extension = QuartzSchedulerTypedExtension(_system)
      extension.scheduleTyped("cronEvery10Seconds", receiver, Tick)

      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveMessages(5, Duration(1, MINUTES))

      receipt must contain(Tock)
      receipt must have size (5)
      extension.cancelJob("cronEvery10Seconds")
    }

    "Properly Setup & Execute a Cron Job via Event Stream" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)
      _system.eventStream.tell(Subscribe(receiver))
      val extension = QuartzSchedulerTypedExtension(_system)
      extension.scheduleTyped[EventStream.Command]("cronEvery12Seconds", _system.eventStream, EventStream.Publish(Tick))


      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveMessages(5, Duration(1, MINUTES))

      receipt must contain(Tock)
      receipt must have size (5)
      extension.cancelJob("cronEvery12Seconds")
    }

    "Delayed Setup & Execute a Cron Job" in {
      val now = Calendar.getInstance()
      val t = now.getTimeInMillis
      val after65s = new Date(t + (35 * 1000))

      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)
      val extension = QuartzSchedulerTypedExtension(_system)
      val jobDt = extension.scheduleTyped("cronEvery15Seconds", receiver, Tick, Some(after65s))

      var receipt = Seq[AnyRef]()
      an[AssertionError] must be thrownBy {
        /* This is a somewhat questionable test as the timing between components may not match the tick off. */
        receipt = probe.receiveMessages(2, Duration(30, SECONDS))
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
      extension.cancelJob("cronEvery15Seconds")
    }
  }

  "The Quartz Scheduling Extension with Reschedule" must {
    "Reschedule an existing Cron Job" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)
      val extension = QuartzSchedulerTypedExtension(_system)
      extension.scheduleTyped("cronEveryEvenSecond", receiver, Tick)

      noException should be thrownBy {
        val newDate = QuartzSchedulerTypedExtension(_system).rescheduleTypedJob("cronEveryEvenSecond", receiver, Tick, None, "0/59 * * ? * *")
        val jobCalender = Calendar.getInstance()
        jobCalender.setTime(newDate)
        jobCalender.get(Calendar.SECOND) mustEqual 59
      }
      extension.cancelJob("cronEveryEvenSecond")
    }
  }

  "Get next trigger date by schedule name" in {
    val receiver = testKit.spawn(ScheduleTestReceiver())
    val probe = testKit.createTestProbe[AnyRef]()
    receiver ! NewProbe(probe.ref)
    val jobDt = QuartzSchedulerTypedExtension(_system).scheduleTyped("cronEveryMidnight", receiver, Tick)
    val nextRun = QuartzSchedulerTypedExtension(_system).nextTrigger("cronEveryMidnight")

    assert(nextRun.getOrElse(new java.util.Date()) == jobDt)
  }

  "The Quartz Scheduling Extension with Dynamic Create" must {
    "Throw exception if creating schedule that already exists" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())

      an[IllegalArgumentException] must be thrownBy {
        QuartzSchedulerTypedExtension(_system).createSchedule("cronEvery10Seconds", None, "*/10 * * ? * *", None)
      }
    }

    "Throw exception if creating a schedule that has invalid cron expression" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())

      an[IllegalArgumentException] must be thrownBy {
        QuartzSchedulerTypedExtension(_system).createSchedule("nonExistingCron", None, "*/10 x * ? * *", None)
      }
    }

    "Add new, schedulable schedule with valid inputs" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      QuartzSchedulerTypedExtension(_system).createSchedule("nonExistingCron", Some("Creating new dynamic schedule"), "*/1 * * ? * *", None)
      val jobDt = QuartzSchedulerTypedExtension(_system).scheduleTyped("nonExistingCron", receiver, Tick)


      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveMessages(5, Duration(30, SECONDS))

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
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      an [IllegalArgumentException] must be thrownBy {
        QuartzSchedulerTypedExtension(_system).createTypedJobSchedule(alreadyExistingScheduleJobName, receiver, Tick, None, "*/10 * * ? * *", None)
      }
    }

    "Throw exception if creating a scheduled job with schedule that has invalid cron expression" in {

      // Remark: Tests are not completely in isolation as using "nonExistingCron"
      // schedule name here would fail because of use and definition in former:
      // "Add new, schedulable schedule with valid inputs" test.
      val nonExistingScheduleJobName = "nonExistingCron_2"

      val receiver = testKit.spawn(ScheduleTestReceiver())

      an [IllegalArgumentException] must be thrownBy {
        QuartzSchedulerTypedExtension(_system).createTypedJobSchedule("nonExistingCron_2", receiver, Tick, None, "*/10 x * ? * *", None)
      }
    }

    "Add new, schedulable job and schedule with valid inputs" in {
      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      val jobDt = QuartzSchedulerTypedExtension(_system).createTypedJobSchedule("nonExistingCron_2", receiver, Tick, Some("Creating new dynamic schedule"), "*/1 * * ? * *", None)

      /* This is a somewhat questionable test as the timing between components may not match the tick off. */
      val receipt = probe.receiveMessages(5, Duration(30, SECONDS))

      receipt must contain(Tock)
      receipt must have size(5)
    }

    "Reschedule an existing job schedule Cron Job" in {

      val toRescheduleJobName = "toRescheduleCron_1"

      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      val jobDt = QuartzSchedulerTypedExtension(_system).createTypedJobSchedule(toRescheduleJobName, receiver, Tick, Some("Creating new dynamic schedule for updateJobSchedule test"), "*/4 * * ? * *")

      noException should be thrownBy {
        val newFirstTimeTriggerDate = QuartzSchedulerTypedExtension(_system).updateTypedJobSchedule(toRescheduleJobName, receiver, Tick, Some("Updating new dynamic schedule for updateJobSchedule test"), "42 * * ? * *")
        val jobCalender = Calendar.getInstance()
        jobCalender.setTime(newFirstTimeTriggerDate)
        jobCalender.get(Calendar.SECOND) mustEqual 42
      }
    }


    "Delete an existing job schedule Cron Job without any error and allow successful creation of new schedule with identical job name" in {

      val toDeleteSheduleJobName = "toBeDeletedscheduleCron_1"

      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      val jobDt = QuartzSchedulerTypedExtension(_system).createTypedJobSchedule(toDeleteSheduleJobName, receiver, Tick, Some("Creating new dynamic schedule for deleteJobSchedule test"), "*/7 * * ? * *")

      noException should be thrownBy {
        // Delete existing scheduled job
        val success = QuartzSchedulerTypedExtension(_system).deleteJobSchedule(toDeleteSheduleJobName)
        if (success) {

          // Create a new schedule job reusing former toDeleteSheduleJobName. This will fail if delebeJobSchedule is not effective.
          val newJobDt = QuartzSchedulerTypedExtension(_system).createTypedJobSchedule(toDeleteSheduleJobName, receiver, Tick, Some("Creating new dynamic schedule after deleteJobSchedule success"), "8 * * ? * *")
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

      val receiver = testKit.spawn(ScheduleTestReceiver())
      val probe = testKit.createTestProbe[AnyRef]()
      receiver ! NewProbe(probe.ref)

      noException should be thrownBy {
        // Deleting non existing scheduled job
        val success = QuartzSchedulerTypedExtension(_system).deleteJobSchedule(nonExistingCronToBeDeleted)
        // must return false
        if (success) {
          fail(s"deleteJobSchedule(${nonExistingCronToBeDeleted}) expected to return false returned true.")
        }
      }
    }

  }

  sealed trait QuartzMessage
  case class NewProbe(probe: ActorRef[AnyRef]) extends QuartzMessage

  case class TockWithFireTime(scheduledFireTime: Long, previousFireTime: Option[Long], nextFireTime: Option[Long]) extends QuartzMessage

  case object Tick extends QuartzMessage

  case object Tock extends QuartzMessage

  object ScheduleTestReceiver {
    private val log: Logger = Logger.getLogger(getClass.getSimpleName)
    var probe: ActorRef[QuartzMessage] = _

    def apply(): Behavior[AnyRef] = Behaviors.receiveMessage {
      case NewProbe(_p) =>
        probe = _p
        Behaviors.same

      case Tick =>
        log.info(s"Got a Tick.")
        probe ! Tock
        Behaviors.same

      case MessageWithFireTime(Tick, scheduledFireTime, previousFireTime, nextFireTime) =>
        log.info(s"Got a Tick for scheduledFireTime=${scheduledFireTime} previousFireTime=${previousFireTime} nextFireTime=${nextFireTime}")
        probe ! TockWithFireTime(
          scheduledFireTime.getTime,
          previousFireTime.map(u => u.getTime),
          nextFireTime.map(u => u.getTime))
        Behaviors.same

      case _ =>
        log.warning("Unmapped message.")
        Behaviors.same
    }
  }

}

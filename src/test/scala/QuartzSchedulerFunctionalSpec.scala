package org.apache.pekko.extension.quartz

import org.apache.pekko.actor._
import org.apache.pekko.japi.Option.Some
import org.apache.pekko.testkit._
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.specs2.runner.JUnitRunner

import java.util.{Calendar, Date}
import scala.concurrent._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class QuartzSchedulerFunctionalSpec(_system: ActorSystem) extends TestKit(_system: ActorSystem)
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
  }

  def this() = this(ActorSystem("QuartzSchedulerFunctionalSpec", SchedulingFunctionalTest.sampleConfiguration))

  "The Quartz Scheduling Extension" must {

    val tickTolerance = SchedulingFunctionalTest.tickTolerance

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
        case TockWithFireTime(scheduledFireTime, _, _) =>
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
      val jobDt = QuartzSchedulerExtension(_system).schedule("cronEvery10SecondsWithFireTimes", receiver, MessageRequireFireTime(Tick))

      val receipt = probe.receiveWhile(Duration(1, MINUTES), Duration(15, SECONDS), 5) {
        case TockWithFireTime(scheduledFireTime, previousFireTime, nextFireTime) =>
          (scheduledFireTime, previousFireTime, nextFireTime)
      }
      0 until 5 foreach { i =>
        val expectedCurrent = jobDt.getTime + i * 10 * 1000
        val expectedPrevious = if (i == 0) 0 else expectedCurrent - 10 * 1000
        val expectedNext = expectedCurrent + 10 * 1000
        assert(receipt(i)._1 === expectedCurrent +- tickTolerance)
        assert(receipt(i)._2.getOrElse(0L) === expectedPrevious +- tickTolerance)
        assert(receipt(i)._3.get === expectedNext +- tickTolerance)
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
      Math.abs(jobCalender.getTimeInMillis - scheduleCalender.getTimeInMillis) <= 1000L mustEqual true
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

  /**
   * Mass operations {suspendAll, resumeAll, deleteAll} combine existing
   * QuartzSchedulerExtension {pauseAll, resumeAll} and adds deleteAll
   * (suspend synonym for pause for consistency).
   */
  "The Quartz Scheduling Extension with Dynamic mass methods" should {

    "Suspend all jobs" ignore {/* TODO implement */}

    "Resume all jobs" ignore {/* TODO implement */}

    "Delete all jobs" in {
      val receiver = _system.actorOf(Props(new ScheduleTestReceiver))
      val probe = TestProbe()
      receiver ! NewProbe(probe.ref)
      val toBeDeletedAllJobName1 = "toBeDeletedAll_1"
      val toBeDeletedAllJobName2 = "toBeDeletedAll_2"
      val jobDt1 = QuartzSchedulerExtension(_system).createJobSchedule(toBeDeletedAllJobName1, receiver, Tick, Some("Creating new dynamic schedule for deleteAll test"), "*/7 * * ? * *")
      val jobDt2 = QuartzSchedulerExtension(_system).createJobSchedule(toBeDeletedAllJobName2, receiver, Tick, Some("Creating new dynamic schedule for deleteAll test"), "*/7 * * ? * *")

      noException should be thrownBy {
        // Deleting all scheduled jobs
        QuartzSchedulerExtension(_system).deleteAll()

        // Create a new schedule job reusing former toBeDeletedAllJobName1. This will fail if deleteAll is not effective.
        val newJobDt1 = QuartzSchedulerExtension(_system).createJobSchedule(toBeDeletedAllJobName1, receiver, Tick, Some("Creating new dynamic schedule after deleteAll success"), "8 * * ? * *")
        val jobCalender1 = Calendar.getInstance()
        jobCalender1.setTime(newJobDt1)
        jobCalender1.get(Calendar.SECOND) mustEqual 8

        // Create a new schedule job reusing former toBeDeletedAllJobName2. This will fail if deleteAll is not effective.
        val newJobDt2 = QuartzSchedulerExtension(_system).createJobSchedule(toBeDeletedAllJobName2, receiver, Tick, Some("Creating new dynamic schedule after deleteAll success"), "9 * * ? * *")
        val jobCalender2 = Calendar.getInstance()
        jobCalender2.setTime(newJobDt2)
        jobCalender2.get(Calendar.SECOND) mustEqual 9
      }
    }
  }

  case class NewProbe(probe: ActorRef)
  case object Tick
  case object Tock
  case class TockWithFireTime(scheduledFireTime: Long, previousFireTime: Option[Long], nextFireTime: Option[Long])

  class ScheduleTestReceiver extends Actor with ActorLogging {
    var probe: ActorRef = _
    def receive = {
      case NewProbe(_p) =>
        probe = _p
      case Tick =>
        log.info(s"Got a Tick.")
        probe ! Tock
      case MessageWithFireTime(Tick, scheduledFireTime, previousFireTime, nextFireTime) =>
        log.info(s"Got a Tick for scheduledFireTime=${scheduledFireTime} previousFireTime=${previousFireTime} nextFireTime=${nextFireTime}")
        probe ! TockWithFireTime(
          scheduledFireTime.getTime,
          previousFireTime.map(u => u.getTime),
          nextFireTime.map(u => u.getTime))
    }
  }

}

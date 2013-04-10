package akka.extension.quartz

import akka.actor._
import akka.event.{LogSource, Logging}

import com.typesafe.config.{ConfigFactory, Config}

import org.quartz.simpl.{RAMJobStore, SimpleThreadPool}
import org.quartz.impl.DirectSchedulerFactory
import java.util.{Date, TimeZone}
import scala.collection.immutable
import org.quartz.{TriggerUtils, Trigger, TriggerBuilder, JobBuilder}
import org.quartz.core.jmx.JobDataMapSupport
import org.quartz.impl.triggers.{SimpleTriggerImpl, CronTriggerImpl}


object QuartzSchedulerExtension extends ExtensionKey[QuartzSchedulerExtension]

/**
 * Note that this extension will only be instantiated *once* *per actor system*.
 *
 */
class QuartzSchedulerExtension(system: ExtendedActorSystem) extends Extension {

  private val log = Logging(system, this)


  // todo - use of the circuit breaker to encapsulate quartz failures?
  def schedulerName = "QuartzScheduler~%s".format(system.name)

  protected val config = system.settings.config.withFallback(defaultConfig).getConfig("akka.quartz").root.toConfig

 // For config values that can be omitted by user, to setup a fallback
  lazy val defaultConfig =  ConfigFactory.parseString("""
    akka.quartz {
      threadPool {
        threadCount = 1
        threadPriority = 5
        daemonThreads = true
      }
      defaultTimezone = UTC
    }
    """.stripMargin)  // todo - boundary checks

  // The # of threads in the pool
  val threadCount = config.getInt("threadPool.threadCount")
  // Priority of threads created. Defaults at 5, can be between 1 (lowest) and 10 (highest)
  val threadPriority = config.getInt("threadPool.threadPriority")
  require(threadPriority >= 1 && threadPriority <= 10,
          "Quartz Thread Priority (akka.quartz.threadPool.threadPriority) must be a positive integer between 1 (lowest) and 10 (highest).")
  // Should the threads we create be daemonic? FYI Non-daemonic threads could make akka / jvm shutdown difficult
  val daemonThreads_? = config.getBoolean("threadPool.daemonThreads")
  // Timezone to use unless specified otherwise
  val defaultTimezone = TimeZone.getTimeZone(config.getString("defaultTimezone"))

  /**
   * Parses job and trigger configurations, preparing them for any code request of a matching job.
   * In our world, jobs and triggers are essentially 'merged'  - our scheduler is built around triggers
   * and jobs are basically 'idiot' programs who fire off messages.
   *
   * RECAST KEY AS UPPERCASE TO AVOID RUNTIME LOOKUP ISSUES
   */
  val schedules: immutable.Map[String, QuartzSchedule] = QuartzSchedules(config, defaultTimezone).map { kv =>
    kv._1.toUpperCase -> kv._2
  }

  log.debug("Configured Schedules: {}", schedules)

  scheduler.start

  initialiseCalendars()


  /**
   * Schedule a job, whose named configuration must be available
   *
   * @return A date, which is what Quartz returns and i'm not sure what it signifies...
   */
  def schedule(name: String, receiver: ActorRef, msg: AnyRef): Date = schedules.get(name.toUpperCase) match {
    case Some(sched) =>
      scheduleJob(name, receiver, msg)(sched)
    case None =>
      throw new IllegalArgumentException("No matching quartz configuration found for schedule '%s'".format(name))
  }

  /**
   * Creates the actual jobs for Quartz, and setups the Trigger, etc.
   */
  protected def scheduleJob(name: String, receiver: ActorRef, msg: AnyRef)(schedule: QuartzSchedule): Date = {
    import scala.collection.JavaConverters._
    log.info("Setting up scheduled job '{}', with '{}'", name, schedule)
    val b = Map.newBuilder[String, AnyRef]
    b += "logBus" -> system.eventStream
    b += "receiver" -> receiver
    b += "message" -> msg

    val jobData = JobDataMapSupport.newJobDataMap(b.result.asJava)
    val job = JobBuilder.newJob(classOf[SimpleActorMessageJob])
                        .withIdentity(name + "_Job")
                        .usingJobData(jobData)
                        .withDescription(schedule.description.getOrElse(null))
                        .build()

    log.debug("Building Trigger.")
    val trigger = schedule.buildTrigger(name)

    log.debug("Scheduling Job '{}' and Trigger '{}'. Is Scheduler Running? {}", job, trigger, scheduler.isStarted)
    scheduler.scheduleJob(job, trigger)
  }


  /**
   * Parses calendar configurations, creates Calendar instances and attaches them to the scheduler
   */
  protected def initialiseCalendars() {
    for ((name, calendar) <- QuartzCalendars(config, defaultTimezone)) {
      log.info("Configuring Calendar '{}'", name)
      // Recast calendar name as upper case to make later lookups easier ( no stupid case clashing at runtime )
      scheduler.addCalendar(name.toUpperCase, calendar, true, true)
    }
  }



  lazy protected val threadPool = {
    // todo - wrap one of the Akka thread pools with the Quartz interface?
    val _tp = new SimpleThreadPool(threadCount, threadPriority)
    _tp.setThreadNamePrefix("AKKA_QRTZ_") // todo - include system name?
    _tp.setMakeThreadsDaemons(daemonThreads_?)
    _tp
  }

  lazy protected val jobStore = {
    // TODO - Make this potentially configurable,  but for now we don't want persistable jobs.
    new RAMJobStore()
  }

  lazy protected val scheduler = {
    // because it's a java API ... initialize the scheduler, THEN get and start it.
    DirectSchedulerFactory.getInstance.createScheduler(schedulerName, system.name, /* todo - will this clash by quartz' rules? */
                                                       threadPool, jobStore)

    val scheduler = DirectSchedulerFactory.getInstance().getScheduler(schedulerName)

    log.debug("Initialized a Quartz Scheduler '{}'", scheduler)

    scheduler
  }

}






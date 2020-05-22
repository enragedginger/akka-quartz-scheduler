package com.typesafe.akka.extension.quartz

import java.text.ParseException
import java.util.{Date, TimeZone}

import akka.actor._
import akka.event.{EventStream, Logging}
import org.quartz._
import org.quartz.core.jmx.JobDataMapSupport
import org.quartz.impl.DirectSchedulerFactory
import org.quartz.simpl.{RAMJobStore, SimpleThreadPool}
import org.quartz.spi.JobStore

import scala.collection.JavaConverters._
import scala.util.control.Exception._


object QuartzSchedulerExtension extends ExtensionId[QuartzSchedulerExtension] with ExtensionIdProvider {
  override def lookup = QuartzSchedulerExtension

  override def createExtension(system: ExtendedActorSystem): QuartzSchedulerExtension =
    new QuartzSchedulerExtension(system)

  override def get(system: ActorSystem): QuartzSchedulerExtension = super.get(system)
}

/**
 * Note that this extension will only be instantiated *once* *per actor system*.
 *
 */
class QuartzSchedulerExtension(system: ExtendedActorSystem) extends Extension {

  private val log = Logging(system, this)


  // todo - use of the circuit breaker to encapsulate quartz failures?
  def schedulerName = "QuartzScheduler~%s".format(system.name)

  protected val config = system.settings.config.getConfig("akka.quartz").root.toConfig

  // The # of threads in the pool
  val threadCount = config.getInt("threadPool.threadCount")
  require(threadCount >= 1, "Quartz Thread Count (akka.quartz.threadPool.threadCount) must be a positive integer.")
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
   */
  val schedules = new scala.collection.concurrent.TrieMap[String, QuartzSchedule]
  schedules ++= QuartzSchedules(config, defaultTimezone)
  val runningJobs = new scala.collection.concurrent.TrieMap[String, JobKey]

  log.debug("Configured Schedules: {}", schedules)

  scheduler.start

  initialiseCalendars()

  /**
   * Puts the Scheduler in 'standby' mode, temporarily halting firing of triggers.
   * Resumable by running 'start'
   */
  def standby(): Unit = scheduler.standby()

  def isInStandbyMode = scheduler.isInStandbyMode

  /**
    * Starts up the scheduler. This is typically used from userspace only to restart
    * a scheduler in standby mode.
    *
    * @return True if calling this function resulted in the starting of the scheduler; false if the scheduler
    *         was already started.
    */
  def start(): Boolean = isStarted match {
    case true =>
      log.warning("Cannot start scheduler, already started.")
      false
    case false =>
      scheduler.start
      true
  }

  def isStarted = scheduler.isStarted

  /**
   * Returns the next Date a schedule will be fired
   */
  def nextTrigger(name: String): Option[Date] = {
    import scala.collection.JavaConverters._
    for {
      jobKey <- runningJobs.get(name)
      trigger <- scheduler.getTriggersOfJob(jobKey).asScala.headOption
    } yield trigger.getNextFireTime
  }

  /**
   * Suspends (pauses) all jobs in the scheduler
   */
  def suspendAll(): Unit = {
    log.info("Suspending all Quartz jobs.")
    scheduler.pauseAll()
  }

  /**
    * Shutdown the scheduler manually. The scheduler cannot be re-started.
    *
    * @param waitForJobsToComplete wait for jobs to complete? default to false
    */
  def shutdown(waitForJobsToComplete: Boolean = false) = {
    scheduler.shutdown(waitForJobsToComplete)
  }

  /**
    * Attempts to suspend (pause) the given job
    *
    * @param name The name of the job, as defined in the schedule
    * @return Success or Failure in a Boolean
    */
  def suspendJob(name: String): Boolean = {
    runningJobs.get(name) match {
      case Some(job) =>
        log.info("Suspending Quartz Job '{}'", name)
        scheduler.pauseJob(job)
        true
      case None =>
        log.warning("No running Job named '{}' found: Cannot suspend", name)
        false
    }
    // TODO - Exception checking?
  }

  /**
    * Attempts to resume (un-pause) the given job
    *
    * @param name The name of the job, as defined in the schedule
    * @return Success or Failure in a Boolean
    */
  def resumeJob(name: String): Boolean = {
    runningJobs.get(name) match {
      case Some(job) =>
        log.info("Resuming Quartz Job '{}'", name)
        scheduler.resumeJob(job)
        true
      case None =>
        log.warning("No running Job named '{}' found: Cannot unpause", name)
        false
    }
    // TODO - Exception checking?
  }

  /**
   * Unpauses all jobs in the scheduler
   */
  def resumeAll(): Unit = {
    log.info("Resuming all Quartz jobs.")
    scheduler.resumeAll()
  }

  /**
    * Cancels the running job and all associated triggers
    *
    * @param name The name of the job, as defined in the schedule
    * @return Success or Failure in a Boolean
    */
  def cancelJob(name: String): Boolean = {
    runningJobs.get(name) match {
      case Some(job) =>
        log.info("Cancelling Quartz Job '{}'", name)
        val result = scheduler.deleteJob(job)
        runningJobs -= name
        result
      case None =>
        log.warning("No running Job named '{}' found: Cannot cancel", name)
        false
    }
    // TODO - Exception checking?
  }

  /**
   * Creates job, associated triggers and corresponding schedule at once. 
   * 
   *
   * @param name The name of the job, as defined in the schedule
   * @param receiver An ActorRef, who will be notified each time the schedule fires
   * @param msg A message object, which will be sent to `receiver` each time the schedule fires
   * @param description A string describing the purpose of the job
   * @param cronExpression A string with the cron-type expression
   * @param calendar An optional calendar to use.
   * @param timezone The time zone to use if different from default.

   * @return A date which indicates the first time the trigger will fire.
   */
  def createJobSchedule(
      name: String, receiver: ActorRef, msg: AnyRef, description: Option[String] = None, 
      cronExpression: String, calendar: Option[String] = None, timezone: TimeZone = defaultTimezone) = { 
    createSchedule(name, description, cronExpression, calendar, timezone)
    schedule(name, receiver, msg)
  }  

  /**
   * Updates job, associated triggers and corresponding schedule at once. 
   * 
   *
   * @param name The name of the job, as defined in the schedule
   * @param receiver An ActorRef, who will be notified each time the schedule fires
   * @param msg A message object, which will be sent to `receiver` each time the schedule fires
   * @param description A string describing the purpose of the job
   * @param cronExpression A string with the cron-type expression
   * @param calendar An optional calendar to use.
   * @param timezone The time zone to use if different from default.

   * @return A date which indicates the first time the trigger will fire.
   */  
  def updateJobSchedule(
      name: String, receiver: ActorRef, msg: AnyRef, description: Option[String] = None, 
      cronExpression: String, calendar: Option[String] = None, timezone: TimeZone = defaultTimezone): Date = {
    rescheduleJob(name, receiver, msg, description, cronExpression, calendar, timezone)
  }  
  
  /**
   * Deletes job, associated triggers and corresponding schedule at once. 
   * 
   * Remark: Identical to `unscheduleJob`. Exists to provide consistent naming of related JobSchedule operations. 
   *
   * @param name The name of the job, as defined in the schedule
   * 
   * @return Success or Failure in a Boolean
   */
  def deleteJobSchedule(name: String): Boolean = unscheduleJob(name)
  
  /**
   * Unschedule an existing schedule 
   * 
   * Cancels the running job and all associated triggers and removes corresponding 
   * schedule entry from internal schedules map.
   *
   * @param name The name of the job, as defined in the schedule
   * 
   * @return Success or Failure in a Boolean
   */
  def unscheduleJob(name: String): Boolean = {
    val isJobCancelled = cancelJob(name)
    if (isJobCancelled) { removeSchedule(name) }
    isJobCancelled
  }

  /**
   * Create a schedule programmatically (must still be scheduled by calling 'schedule')
   *
   * @param name A String identifying the job
   * @param description A string describing the purpose of the job
   * @param cronExpression A string with the cron-type expression
   * @param calendar An optional calendar to use.
   *
   */
  def createSchedule(name: String, description: Option[String] = None, cronExpression: String, calendar: Option[String] = None,
                     timezone: TimeZone = defaultTimezone) = {
    val expression = catching(classOf[ParseException]) either new CronExpression(cronExpression) match {
      case Left(t) =>
        throw new IllegalArgumentException(s"Invalid 'expression' for Cron Schedule '$name'. Failed to validate CronExpression.", t)
      case Right(expr) =>
        expr
    }

    val schedule = new QuartzCronSchedule(name, description, expression, timezone, calendar)
    schedules.putIfAbsent(name, schedule) match {
      case Some(_) =>
        throw new IllegalArgumentException(s"A schedule with this name already exists: [$name]")
      case None =>
        ()
    }
  }

  /**
    * Reschedule a job
    *
    * @param name           A String identifying the job
    * @param receiver       An ActorRef, who will be notified each time the schedule fires
    * @param msg            A message object, which will be sent to `receiver` each time the schedule fires
    * @param description    A string describing the purpose of the job
    * @param cronExpression A string with the cron-type expression
    * @param calendar       An optional calendar to use.
    * @return A date which indicates the first time the trigger will fire.
    */

  def rescheduleJob(name: String, receiver: ActorRef, msg: AnyRef, description: Option[String] = None,
                    cronExpression: String, calendar: Option[String] = None, timezone: TimeZone = defaultTimezone): Date = {
    cancelJob(name)
    removeSchedule(name)
    createSchedule(name, description, cronExpression, calendar, timezone)
    scheduleInternal(name, receiver, msg, None)
  }

  private def removeSchedule(name: String) = schedules -= name

  /**
    * Schedule a job, whose named configuration must be available
    *
    * @param name     A String identifying the job, which must match configuration
    * @param receiver An ActorRef, who will be notified each time the schedule fires
    * @param msg      A message object, which will be sent to `receiver` each time the schedule fires
    * @return A date which indicates the first time the trigger will fire.
    */
  def schedule(name: String, receiver: ActorRef, msg: AnyRef): Date = scheduleInternal(name, receiver, msg, None)


  /**
    * Schedule a job, whose named configuration must be available
    *
    * @param name     A String identifying the job, which must match configuration
    * @param receiver An ActorSelection, who will be notified each time the schedule fires
    * @param msg      A message object, which will be sent to `receiver` each time the schedule fires
    * @return A date which indicates the first time the trigger will fire.
    */
  def schedule(name: String, receiver: ActorSelection, msg: AnyRef): Date = scheduleInternal(name, receiver, msg, None)


  /**
    * Schedule a job, whose named configuration must be available
    *
    * @param name     A String identifying the job, which must match configuration
    * @param receiver An EventStream, who will be published to each time the schedule fires
    * @param msg      A message object, which will be published to `receiver` each time the schedule fires
    * @return A date which indicates the first time the trigger will fire.
    */
  def schedule(name: String, receiver: EventStream, msg: AnyRef): Date = scheduleInternal(name, receiver, msg, None)

  /**
    * Schedule a job, whose named configuration must be available
    *
    * @param name     A String identifying the job, which must match configuration
    * @param receiver An ActorRef, who will be notified each time the schedule fires
    * @param msg      A message object, which will be sent to `receiver` each time the schedule fires
    * @return A date which indicates the first time the trigger will fire.
    */
  def schedule(name: String, receiver: ActorRef, msg: AnyRef, startDate: Option[Date]): Date = scheduleInternal(name, receiver, msg, startDate)


  /**
    * Schedule a job, whose named configuration must be available
    *
    * @param name     A String identifying the job, which must match configuration
    * @param receiver An ActorSelection, who will be notified each time the schedule fires
    * @param msg      A message object, which will be sent to `receiver` each time the schedule fires
    * @return A date which indicates the first time the trigger will fire.
    */
  def schedule(name: String, receiver: ActorSelection, msg: AnyRef, startDate: Option[Date]): Date = scheduleInternal(name, receiver, msg, startDate)

  /**
    * Schedule a job, whose named configuration must be available
    *
    * @param name     A String identifying the job, which must match configuration
    * @param receiver An EventStream, who will be published to each time the schedule fires
    * @param msg      A message object, which will be published to `receiver` each time the schedule fires
    * @return A date which indicates the first time the trigger will fire.
    */
  def schedule(name: String, receiver: EventStream, msg: AnyRef, startDate: Option[Date]): Date = scheduleInternal(name, receiver, msg, startDate)

  /**
    * Helper method for schedule because overloaded methods can't have default parameters.
    * @param name The name of the schedule / job.
    * @param receiver The receiver of the job message. This must be either an ActorRef or an ActorSelection.
    * @param msg The message to send to kick off the job.
    * @param startDate The optional date indicating the earliest time the job may fire.
    * @return A date which indicates the first time the trigger will fire.
    */
  private def scheduleInternal(name: String, receiver: AnyRef, msg: AnyRef, startDate: Option[Date]): Date = schedules.get(name) match {
    case Some(schedule) => scheduleJob(name, receiver, msg, startDate)(schedule)
    case None => throw new IllegalArgumentException("No matching quartz configuration found for schedule '%s'".format(name))
  }


  /**
   * Creates the actual jobs for Quartz, and setups the Trigger, etc.
   *
   * @return A date, which indicates the first time the trigger will fire.
   */
  protected def scheduleJob(name: String, receiver: AnyRef, msg: AnyRef, startDate: Option[Date])(schedule: QuartzSchedule): Date = {
    import scala.collection.JavaConverters._
    log.info("Setting up scheduled job '{}', with '{}'", name, schedule)
    val jobDataMap = Map[String, AnyRef](
      "logBus" -> system.eventStream,
      "receiver" -> receiver,
      "message" -> msg
    )

    val jobData = JobDataMapSupport.newJobDataMap(jobDataMap.asJava)
    val job = JobBuilder.newJob(classOf[SimpleActorMessageJob])
      .withIdentity(name + "_Job")
      .usingJobData(jobData)
      .withDescription(schedule.description.orNull)
      .build()

    log.debug("Adding jobKey {} to runningJobs map.", job.getKey)

    runningJobs += name -> job.getKey

    log.debug("Building Trigger with startDate '{}", startDate.getOrElse(new Date()))
    val trigger = schedule.buildTrigger(name, startDate)

    log.debug("Scheduling Job '{}' and Trigger '{}'. Is Scheduler Running? {}", job, trigger, scheduler.isStarted)
    scheduler.scheduleJob(job, trigger)
  }


  /**
   * Parses calendar configurations, creates Calendar instances and attaches them to the scheduler
   */
  protected def initialiseCalendars() {
    for ((name, calendar) <- QuartzCalendars(config, defaultTimezone)) {
      log.info("Configuring Calendar '{}'", name)
      scheduler.addCalendar(name, calendar, true, true)
    }
    log.info(s"Initialized calendars: ${scheduler.getCalendarNames.asScala mkString ","}")
  }



  lazy protected val threadPool = {
    // todo - wrap one of the Akka thread pools with the Quartz interface?
    val _tp = new SimpleThreadPool(threadCount, threadPriority)
    _tp.setThreadNamePrefix("AKKA_QRTZ_") // todo - include system name?
    _tp.setMakeThreadsDaemons(daemonThreads_?)
    _tp
  }

  lazy protected val jobStore: JobStore = {
    // TODO - Make this potentially configurable,  but for now we don't want persistable jobs.
    new RAMJobStore()
  }

  lazy protected val scheduler = {
    // because it's a java API ... initialize the scheduler, THEN get and start it.
    DirectSchedulerFactory.getInstance.createScheduler(schedulerName, system.name, /* todo - will this clash by quartz' rules? */
      threadPool, jobStore)

    val scheduler = DirectSchedulerFactory.getInstance().getScheduler(schedulerName)

    log.debug("Initialized a Quartz Scheduler '{}'", scheduler)

    system.registerOnTermination({
      log.info("Shutting down Quartz Scheduler with ActorSystem Termination (Any jobs awaiting completion will end as well, as actors are ending)...")
      scheduler.shutdown(false)
    })

    scheduler
  }

}

package akka.extension.quartz

import akka.actor._
import akka.event.{LogSource, Logging}

import com.typesafe.config.{ConfigFactory, Config}

import org.quartz.simpl.{RAMJobStore, SimpleThreadPool}
import org.quartz.impl.DirectSchedulerFactory


object QuartzSchedulerExtension extends ExtensionKey[QuartzSchedulerExtension]

/**
 * Note that this extension will only be instantiated *once* *per actor system*.
 *
 */
class QuartzSchedulerExtension(system: ExtendedActorSystem) extends Extension {

  private val log = Logging(system, this)


  // todo - use of the circuit breaker to encapsulate quartz failures?
  val schedulerName = "QuartzScheduler~".format(system.name)

  protected val config = system.settings.config.withFallback(defaultConfig)

 // For config values that can be omitted by user, to setup a fallback
  lazy val defaultConfig =  ConfigFactory.parseString("""
    akka.quartz {
      threadPool {
        threadCount = -1
        threadPriority = 5
        daemonThreads = true
      }
    }
    """.stripMargin)  // todo - boundary checks

  // The # of threads in the pool
  val threadCount = config.getInt("akka.quartz.threadPool.threadCount")
  // Priority of threads created. Defaults at 5, can be between 1 (lowest) and 10 (highest)
  val threadPriority = config.getInt("akka.quartz.threadPool.threadPriority")
  require(threadPriority >= 1 && threadPriority <= 10,
          "Quartz Thread Priority (akka.quartz.threadPool.threadPriority) must be a positive integer between 1 (lowest) and 10 (highest).")
  // Should the threads we create be daemonic? FYI Non-daemonic threads could make akka / jvm shutdown difficult
  val daemonThreads_? = config.getBoolean("akka.quartz.threadPool.daemonThreads")

  initialiseCalendars()

  /**
   * Parses calendar configurations, creates Calendar instances and attaches them to the scheduler
   */
  protected def initialiseCalendars() = {

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

    log.debug("Initialized a Quartz Scheduler '%s'", scheduler)

    scheduler
  }

}

object `package` {
  implicit val quartzExtensionLoggerType: LogSource[QuartzSchedulerExtension] = new LogSource[QuartzSchedulerExtension] {
    def genString(t: QuartzSchedulerExtension): String = "[" + t.schedulerName + "]"
  }
}





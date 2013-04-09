package akka.extension.quartz

import akka.event.LogSource

/**
 * Package imports, for things like implicits shared across the system.
 */
object `package` {
  implicit val quartzExtensionLoggerType: LogSource[QuartzSchedulerExtension] = new LogSource[QuartzSchedulerExtension] {
    def genString(t: QuartzSchedulerExtension): String = "[" + t.schedulerName + "]"
  }

  implicit val quartzJobLoggerType: LogSource[SimpleActorMessageJob] = new LogSource[SimpleActorMessageJob] {
    def genString(t: SimpleActorMessageJob): String = "[QuartzJob]"
  }
}

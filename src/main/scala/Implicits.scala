package org.apache.pekko.extension.quartz

import org.apache.pekko.event.LogSource

/**
 * Package imports, for things like implicits shared across the system.
 */
object `package` {
  implicit val quartzExtensionLoggerType: LogSource[QuartzSchedulerExtension] = (t: QuartzSchedulerExtension) => "[" + t.schedulerName + "]"

  implicit val quartzJobLoggerType: LogSource[SimpleActorMessageJob] = (_: SimpleActorMessageJob) => "[QuartzJob]"
}

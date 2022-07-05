package com.typesafe.akka.extension.quartz

import akka.event.LogSource

/**
 * Package imports, for things like implicits shared across the system.
 */
object `package` {
  implicit val quartzExtensionLoggerType: LogSource[QuartzSchedulerExtension] = (t: QuartzSchedulerExtension) => "[" + t.schedulerName + "]"

  implicit val quartzJobLoggerType: LogSource[SimpleActorMessageJob] = (_: SimpleActorMessageJob) => "[QuartzJob]"
}

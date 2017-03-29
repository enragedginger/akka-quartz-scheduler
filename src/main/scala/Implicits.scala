package com.typesafe.akka.extension.quartz

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

  implicit def scalaFunctionToJavaBiFunction[From1, From2, To](function: (From1, From2) => To): java.util.function.BiFunction[From1, From2, To] = {
    new java.util.function.BiFunction[From1, From2, To] {
      override def apply(input: From1, value: From2): To = function(input, value)
    }
  }
}

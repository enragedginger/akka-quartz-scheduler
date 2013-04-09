/**
 * Copyright (c) 2011-2013 Brendan W. McAdams <http://evilmonkeylabs.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package akka.extension.quartz

import akka.event.LogSource

/**
 * Package imports, for things like implicits shared across the system.
 */
object `package` {
  implicit val quartzExtensionLoggerType: LogSource[QuartzSchedulerExtension] = new LogSource[QuartzSchedulerExtension] {
    def genString(t: QuartzSchedulerExtension): String = "[" + t.schedulerName + "]"
  }

  implicit val quartzJobLoggerType: LogSource[QuartzJob] = new LogSource[QuartzJob] {
    def genString(t: QuartzJob): String = "[QuartzJob]"
  }
}

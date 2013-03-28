package akka.extension.quartz
package test

import org.specs2.runner.JUnitRunner
import org.specs2.Specification
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations

@RunWith(classOf[JUnitRunner])
class QuartzCalendarSpec extends Specification with ThrownExpectations { def is =

  "This is a specification to validate the behavior of the Quartz Calendar configuration modelling"   ^
                                                            p ^
  "The configuration parser should"                           ^
    "Fetch a list of all calenders in a configuration block"  ! parseCalendarList ^
    "Be able to parse and create an Annual calendar"          ! parseAnnual ^
    "Be able to parse and create a Holiday calendar"          ! parseHoliday ^
    "Be able to parse and create a Daily calendar"            ! parseDaily ^
    "Be able to parse and create a Monthly calendar"          ! parseMonthly ^
    "Be able to parse and create a Weekly calendar"           ! parseWeekly ^
    "Be able to parse and create a Cron calendar"             ! parseCronStyle ^
                                                                end

  def parseCalendarList = {
    todo
  }

  def parseAnnual = {
    todo
  }
  def parseHoliday = {
    todo
  }
  def parseDaily = {
    todo
  }
  def parseMonthly = {
    todo
  }
  def parseWeekly = {
    todo
  }
  def parseCronStyle = {
    todo
  }


}

package com.typesafe.akka.extension.quartz
package test

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.specs2.Specification
import org.specs2.matcher.ThrownExpectations
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConfigSpec extends Specification with ThrownExpectations { def is =
  sequential ^
  "This is a specification of the default configuration of the QuartzSchedulerExtension" ^ p ^
  "The reference configuration should" ^
    "contain all default values to setup a thread pool"       ! parseReferenceThreadPool ^
    "contain the default timezone ID"                         ! parseReferenceTimezone ^
                                                                end

  lazy val reference = ConfigFactory.load("reference.conf")

  def parseReferenceThreadPool = {
    reference.getInt("akka.quartz.threadPool.threadCount") mustEqual 1
    reference.getInt("akka.quartz.threadPool.threadPriority") mustEqual 5
    reference.getBoolean("akka.quartz.threadPool.daemonThreads") mustEqual true
    reference.getInt("akka.quartz.threadPool.threadCount") mustEqual 1
  }

  def parseReferenceTimezone = {
    reference.getString("akka.quartz.defaultTimezone") mustEqual "UTC"
  }

}

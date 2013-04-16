package com.typesafe.akka.extension.quartz

import org.quartz.{JobExecutionException, JobDataMap, JobExecutionContext, Job}
import akka.event.{LoggingBus, Logging}
import akka.actor.ActorRef

/**
 * Base trait, in case we decide to diversify down the road
 * and allow users to pick "types" of jobs, we still want
 * strict control over them monkeying around in ways that
 * exposes the "bad" parts of Quartz –
 * such as persisted mutable state
 */
sealed trait QuartzJob extends Job {
  def jobType: String

  /**
   * Fetch an item, cast to a specific type, from the JobDataMap.
   * I could just use apply, but I want to have a cleaner 'not there' error.
   *
   * This does not return Option and flatly explodes upon a key being missing.
   *
   * TODO - NotNothing check?
   **/
  protected def as[T](key: String)(implicit dataMap: JobDataMap): T = Option(dataMap.get(key)) match {
    case Some(item) =>
      // todo - more careful casting check?
      item.asInstanceOf[T]
    case None =>
      throw new NoSuchElementException("No entry in JobDataMap for required entry '%s'".format(key))
  }

  /**
   * Fetch an item, cast to a specific type, from the JobDataMap.
   * I could just use apply, but I want to have a cleaner 'not there' error.
   *
   * TODO - NotNothing check?
   **/
  protected def getAs[T](key: String)(implicit dataMap: JobDataMap): Option[T] = Option(dataMap.get(key)) match {
    case Some(item) =>
      // todo - more careful casting check?
      Some(item.asInstanceOf[T])
    case None => None
  }
}

class SimpleActorMessageJob extends Job {
  val jobType = "SimpleActorMessage"

  /**
   * Fetch an item, cast to a specific type, from the JobDataMap.
   * I could just use apply, but I want to have a cleaner 'not there' error.
   *
   * This does not return Option and flatly explodes upon a key being missing.
   *
   * TODO - NotNothing check?
   **/
  protected def as[T](key: String)(implicit dataMap: JobDataMap): T = Option(dataMap.get(key)) match {
    case Some(item) =>
      // todo - more careful casting check?
      item.asInstanceOf[T]
    case None =>
      throw new NoSuchElementException("No entry in JobDataMap for required entry '%s'".format(key))
  }

  /**
   * Fetch an item, cast to a specific type, from the JobDataMap.
   * I could just use apply, but I want to have a cleaner 'not there' error.
   *
   * TODO - NotNothing check?
   **/
  protected def getAs[T](key: String)(implicit dataMap: JobDataMap): Option[T] = Option(dataMap.get(key)) match {
    case Some(item) =>
      // todo - more careful casting check?
      Some(item.asInstanceOf[T])
    case None => None
  }
  /**
   * These jobs are fundamentally ephemeral - a new Job is created
   * each time we trigger, and passed a context which contains, among
   * other things, a JobDataMap, which transfers mutable state
   * from one job trigger to another
   *
   * @throws JobExecutionException
   */
  def execute(context: JobExecutionContext) {

    implicit val dataMap = context.getJobDetail.getJobDataMap

    val key  = context.getJobDetail.getKey

    try {

      val logBus = as[LoggingBus]("logBus")

      val receiver = as[ActorRef]("receiver")

      /**
       * Message is an instance, essentially static, not a class to be instantiated.
       * JobDataMap uses AnyRef, while message can be any (though do we really want to support value classes?)
       * so this casting (and the initial save into the map) may involve boxing.
       **/
      val msg = dataMap.get("message")

      val log = Logging(logBus, this)

      log.debug("Triggering job '{}', sending '{}' to '{}'", key.getName, msg, receiver)

      receiver ! msg
    } catch {
      // All exceptions thrown from a job, including Runtime, must be wrapped in a JobExcecutionException or Quartz ignores it
      case jee: JobExecutionException => throw jee
      case t: Throwable =>
        throw new JobExecutionException("ERROR executing Job '%s': '%s'".format(key.getName, t.getMessage), t) // todo - control refire?
    }
  }
}

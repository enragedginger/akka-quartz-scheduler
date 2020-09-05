package com.typesafe.akka.extension.quartz

import java.util.Date

/**
  * wrap msg with scheduledFireTime
  */

trait FireTimeMessage {
  def msg: AnyRef
}

final case class MessageWithFireTime(msg: AnyRef, scheduledFireTime:Date) extends FireTimeMessage
final case class MessageWithFireTimes(msg: AnyRef, previousFiringTime: Option[Date], currentFiringTime: Option[Date], nextFiringTime: Option[Date]) extends FireTimeMessage

trait FiringTimeField

case object NextFiringTime extends FiringTimeField
case object PreviousFiringTime extends FiringTimeField
case object CurrentFiringTime extends FiringTimeField
case object AllFiringTimes extends FiringTimeField

final case class MessageRequireFireTime(msg: AnyRef, fields: FiringTimeField=CurrentFiringTime)
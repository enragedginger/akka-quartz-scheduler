package akka.extension.quartz


/**
 * Utility classes around the creation and configuration of Quartz Calendars.
 * All dates must be ISO-8601 compliant.
 */
object QuartzCalendar {
  // TODO - Support a default / "Base" calendar.
  // type (of enum list)
  // timezone (parseable)
  // description (string)

  /* annual
   *   excludes a set of days of the year
   *   e.g. bank holidays which are on same date every year
   *   doesn't take year into account, but can't calculate moveable feasts.
   *   i.e. you can specify "christmas" (always december 25) and it will get every christmas,
   *   but not "Easter" (which is calculated based on the first full moon on or after the spring equinox)
   */
  // excludeDates (list of ISO-8601 dates, Year can be set to anything as YYYY-MM-DD
  //               or MM-DD will fill in a default )

  /* holiday
   *  excludes full specified day, with the year taken into account
   */
  // excludeDates (list of ISO-8601 dates, YYYY-MM-DD)

  /* daily
   *  excludes a specified time range each day. cannot cross daily boundaries.
   *  only one time range PER CALENDAR.
   */
  // exclude block {
  //    startTime = ISO-8601 Time down to millis (HH:mm:ss:MM)
  //    endTime   = ISO-8601 Time down to millis (HH:mm:ss:MM)
  // }

  /* monthly
   *  excludes a set of days of the month.
   */
  // excludeDays (list of ints from 1-31)

  /* weekly
   *  Excludes a set of days of the week, by default excludes Saturday and Sunday
   */
  // excludeDays (list of ints from 1-7 where 1 is sunday, 7 is saturday) Also recognizes day name in full spelling TODO: Abbrevs
  // excludeWeekends (boolean) By default TRUE, *overriden by excludeDays* (e.g. if you say this is true but exclude sunday, exclude wins)

  /* cronStyle
   *  excludes the set of times expressed by a given [Quartz CronExpression](http://quartz-scheduler.org/api/2.1.7/org/quartz/CronExpression.html)
   *  Gets *one* expression set on it.
   *
   */
  // excludeExpression (Valid Quartz CronExpression)

//  def parseCalendars(config)
}

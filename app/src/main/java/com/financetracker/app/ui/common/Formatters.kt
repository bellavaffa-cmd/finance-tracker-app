package com.financetracker.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun Long.toLocalTime(): LocalTime = Instant.ofEpochMilli(this).atZone(zone).toLocalTime()

fun LocalDate.atStartOfDayMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** Keeps the time-of-day when only the date part is being changed by a picker. */
fun LocalDate.withTimeFrom(millis: Long): Long =
    atTime(millis.toLocalTime()).atZone(zone).toInstant().toEpochMilli()

private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val dayHeaderWithYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** "Today" / "Yesterday" / "Tuesday 12 August" - the header above a day's transactions. */
fun dayHeader(millis: Long): String {
    val date = millis.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayHeaderFormatter) else date.format(dayHeaderWithYear)
    }
}

/** Compact date for chips and rows, with the year shown only when it is not the current one. */
fun shortDate(millis: Long): String {
    val date = millis.toLocalDate()
    return if (date.year == LocalDate.now().year) date.format(shortDateFormatter)
    else date.format(fullDateFormatter)
}

fun fullDate(millis: Long): String = millis.toLocalDate().format(fullDateFormatter)

fun timeOfDay(millis: Long): String = millis.toLocalTime().format(timeFormatter)

/** "in 3 days" / "tomorrow" / "2 days overdue" - how a recurring rule describes its next run. */
fun relativeDueLabel(dueMillis: Long): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueMillis.toLocalDate())
    return when {
        days < -1L -> "${-days} days overdue"
        days == -1L -> "1 day overdue"
        days == 0L -> "due today"
        days == 1L -> "due tomorrow"
        days < 30L -> "in $days days"
        else -> "on ${shortDate(dueMillis)}"
    }
}

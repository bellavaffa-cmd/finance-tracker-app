package com.financetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A budgeting month, which is not necessarily a calendar month.
 *
 * If you are paid on the 25th, budgeting from the 1st means every month's figures are split across
 * two pay cycles and none of them mean anything. [startDay] moves the boundary: the period called
 * "August 2026" runs from 25 August to 24 September inclusive when [startDay] is 25, and from
 * 1 August to 31 August when it is 1.
 */
data class MonthPeriod(
    val year: Int,
    val month: Int,
    val startDay: Int
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val startDate: LocalDate get() = dateIn(year, month, startDay)

    val endDateExclusive: LocalDate
        get() {
            val next = YearMonth.of(year, month).plusMonths(1)
            return dateIn(next.year, next.monthValue, startDay)
        }

    val startMillis: Long get() = startDate.atStartOfDay(zone).toInstant().toEpochMilli()

    val endMillisExclusive: Long get() = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    /** "August 2026". */
    val label: String
        get() = "${YearMonth.of(year, month).month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $year"

    /** "Aug 2026", for chart axes and chips. */
    val shortLabel: String
        get() = "${YearMonth.of(year, month).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $year"

    /** "25 Aug - 24 Sep", shown only when the period is not a calendar month. */
    val rangeLabel: String?
        get() {
            if (startDay == 1) return null
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            return "${startDate.format(fmt)} - ${endDateExclusive.minusDays(1).format(fmt)}"
        }

    fun plusMonths(delta: Long): MonthPeriod {
        val ym = YearMonth.of(year, month).plusMonths(delta)
        return MonthPeriod(ym.year, ym.monthValue, startDay)
    }

    fun contains(millis: Long): Boolean = millis >= startMillis && millis < endMillisExclusive

    /** Chronological ordering key, safe to compare across years. */
    val ordinal: Int get() = year * 12 + (month - 1)

    companion object {
        /** Day-of-month is capped at 28 so every month has one, avoiding February special cases. */
        const val MAX_START_DAY = 28

        private fun dateIn(year: Int, month: Int, day: Int): LocalDate {
            val ym = YearMonth.of(year, month)
            return ym.atDay(day.coerceIn(1, minOf(ym.lengthOfMonth(), MAX_START_DAY)))
        }

        /** The period that [millis] falls into for the given boundary day. */
        fun containing(millis: Long, startDay: Int): MonthPeriod {
            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            val safeStart = startDay.coerceIn(1, MAX_START_DAY)
            // Before the boundary day, you are still inside the period that began last month.
            val anchor = if (date.dayOfMonth >= safeStart) YearMonth.from(date) else YearMonth.from(date).minusMonths(1)
            return MonthPeriod(anchor.year, anchor.monthValue, safeStart)
        }

        fun current(startDay: Int): MonthPeriod = containing(System.currentTimeMillis(), startDay)

        /** The [count] periods ending with [last], oldest first - the x-axis of the trend chart. */
        fun trailing(last: MonthPeriod, count: Int): List<MonthPeriod> =
            (count - 1 downTo 0).map { last.plusMonths(-it.toLong()) }
    }
}

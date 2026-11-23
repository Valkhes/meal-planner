package dev.valkhes.mealplanner.domain.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Consecutive days, both ends included. */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    /** How many of this range's days fall strictly after [after] and up to [until] included. */
    fun daysWithin(
        after: LocalDate,
        until: LocalDate,
    ): Int {
        val from = maxOf(start, after.plusDays(1))
        val to = minOf(endInclusive, until)
        return if (from > to) 0 else from.daysUntil(to) + 1
    }
}

/** Days of [ranges] strictly after [after] and up to [until], each day counted once even where ranges overlap. */
fun List<DateRange>.daysWithin(
    after: LocalDate,
    until: LocalDate,
): Int {
    var count = 0
    var day = after.plusDays(1)
    while (day <= until) {
        if (any { day in it }) count++
        day = day.plusDays(1)
    }
    return count
}

private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)

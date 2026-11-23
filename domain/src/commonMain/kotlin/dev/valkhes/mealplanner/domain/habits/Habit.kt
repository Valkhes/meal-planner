package dev.valkhes.mealplanner.domain.habits

import dev.valkhes.mealplanner.domain.shared.DateRange
import dev.valkhes.mealplanner.domain.shared.daysWithin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * How a household eats one dish, learned only from when it was really eaten (README, constraint 5).
 * Days away do not count: a curry every 10 days is still every 10 days around a week of holidays.
 */
data class Habit(
    val count: Int,
    val last: LocalDate?,
    /** Median days between two days it was eaten; null until there are [MIN_DAYS_FOR_INTERVAL] distinct days. */
    val typicalIntervalDays: Int?,
) {
    /**
     * How overdue the dish is: 1.0 means "exactly its usual interval since last time", 2.0 "twice as
     * long". Null without an interval. It ranks the dishes worth suggesting.
     */
    fun dueRatio(
        today: LocalDate,
        away: List<DateRange> = emptyList(),
    ): Double? {
        val interval = typicalIntervalDays ?: return null
        return (daysSince(today, away) ?: return null).toDouble() / interval.coerceAtLeast(1)
    }

    /** Days at home since it was last eaten. */
    fun daysSince(
        today: LocalDate,
        away: List<DateRange> = emptyList(),
    ): Int? {
        val last = last ?: return null
        return (last.daysUntil(today) - away.daysWithin(last, today)).coerceAtLeast(0)
    }

    companion object {
        const val MIN_DAYS_FOR_INTERVAL: Int = 3

        /** [occasionDates] has one date per occasion: a dish eaten at lunch and dinner the same day appears twice. */
        fun of(
            occasionDates: List<LocalDate>,
            away: List<DateRange> = emptyList(),
        ): Habit {
            if (occasionDates.isEmpty()) return Habit(0, null, null)
            val days = occasionDates.distinct().sorted()
            val interval =
                if (days.size < MIN_DAYS_FOR_INTERVAL) {
                    null
                } else {
                    median(days.zipWithNext { a, b -> a.daysUntil(b) - away.daysWithin(a, b.minusOneDay()) })
                }
            return Habit(occasionDates.size, days.last(), interval)
        }

        private fun LocalDate.minusOneDay(): LocalDate = LocalDate.fromEpochDays(toEpochDays() - 1)

        private fun median(values: List<Int>): Int {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle] + 1) / 2
        }
    }
}

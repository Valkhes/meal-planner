package dev.valkhes.mealplanner.domain.habits

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** How a household eats one dish, learned only from when it was really eaten (README, constraint 5). */
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
    fun dueRatio(today: LocalDate): Double? {
        val interval = typicalIntervalDays ?: return null
        val since = last?.daysUntil(today) ?: return null
        return since.toDouble() / interval.coerceAtLeast(1)
    }

    companion object {
        const val MIN_DAYS_FOR_INTERVAL: Int = 3

        /** [occasionDates] has one date per occasion: a dish eaten at lunch and dinner the same day appears twice. */
        fun of(occasionDates: List<LocalDate>): Habit {
            if (occasionDates.isEmpty()) return Habit(0, null, null)
            val days = occasionDates.distinct().sorted()
            val interval =
                if (days.size < MIN_DAYS_FOR_INTERVAL) {
                    null
                } else {
                    median(days.zipWithNext { a, b -> a.daysUntil(b) })
                }
            return Habit(occasionDates.size, days.last(), interval)
        }

        private fun median(values: List<Int>): Int {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle] + 1) / 2
        }
    }
}

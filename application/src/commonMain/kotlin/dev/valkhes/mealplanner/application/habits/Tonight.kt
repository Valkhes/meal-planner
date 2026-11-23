package dev.valkhes.mealplanner.application.habits

import dev.valkhes.mealplanner.domain.cookbook.Cookbook
import dev.valkhes.mealplanner.domain.cookbook.Dish
import dev.valkhes.mealplanner.domain.habits.Habit
import dev.valkhes.mealplanner.domain.journal.Journal
import dev.valkhes.mealplanner.domain.shared.DateRange
import kotlinx.datetime.LocalDate

/** Why a dish is suggested tonight, so the user can trust or dismiss it at a glance. */
sealed interface Reason {
    /** "Not made for 12 days, usually every 10." */
    data class Rhythm(
        val daysSince: Int,
        val intervalDays: Int,
    ) : Reason

    /** "Made 2 times, last 5 days ago": no rhythm learned yet. */
    data class Frequent(
        val count: Int,
        val daysSince: Int,
    ) : Reason
}

data class TonightSuggestion(
    val dish: Dish,
    val habit: Habit,
    val reason: Reason,
)

/**
 * The answer to "what do we eat tonight?". [learning] stays true during the break-in period
 * (README, constraint 5): the suggestions are then only the household's most frequent dishes.
 */
data class Tonight(
    val suggestions: List<TonightSuggestion>,
    val mealsLogged: Int,
    val learning: Boolean,
)

object TonightView {
    /** Logged occasions before the advice is worth anything. */
    const val BREAK_IN_MEALS: Int = 10
    const val SIZE: Int = 3

    /**
     * Dishes that fit in [availableMinutes] (null: any time; a dish without a known time always
     * fits), not eaten today, the most due against their own rhythm first, days away not counted.
     */
    fun of(
        cookbook: Cookbook,
        journal: Journal,
        away: List<DateRange>,
        today: LocalDate,
        availableMinutes: Int?,
    ): Tonight {
        val summaries = HabitViews.summaries(cookbook, journal, away)
        val candidates =
            summaries
                .filter { it.habit.count > 0 && it.habit.last != today }
                .filter { s ->
                    availableMinutes == null ||
                        s.dish.recipe.minutes
                            .let { it == null || it <= availableMinutes }
                }.sortedWith(
                    compareByDescending<DishSummary> { score(it.habit, today, away) }
                        .thenByDescending { it.habit.count }
                        .thenByDescending { it.habit.last },
                ).take(SIZE)
        val logged = journal.occasions().size
        return Tonight(
            suggestions = candidates.map { TonightSuggestion(it.dish, it.habit, reason(it.habit, today, away)) },
            mealsLogged = logged,
            learning = logged < BREAK_IN_MEALS,
        )
    }

    /** How due a dish is; without a rhythm yet, a dish made several times beats one made once. */
    private fun score(
        habit: Habit,
        today: LocalDate,
        away: List<DateRange>,
    ): Double = habit.dueRatio(today, away) ?: if (habit.count >= 2) NO_RHYTHM_FREQUENT else NO_RHYTHM_ONCE

    private fun reason(
        habit: Habit,
        today: LocalDate,
        away: List<DateRange>,
    ): Reason {
        val since = habit.daysSince(today, away) ?: 0
        val interval = habit.typicalIntervalDays
        return if (interval != null) Reason.Rhythm(since, interval) else Reason.Frequent(habit.count, since)
    }

    private const val NO_RHYTHM_FREQUENT: Double = 0.5
    private const val NO_RHYTHM_ONCE: Double = 0.2
}

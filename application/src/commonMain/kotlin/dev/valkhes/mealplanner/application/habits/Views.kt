package dev.valkhes.mealplanner.application.habits

import dev.valkhes.mealplanner.domain.cookbook.Cookbook
import dev.valkhes.mealplanner.domain.cookbook.Dish
import dev.valkhes.mealplanner.domain.habits.Habit
import dev.valkhes.mealplanner.domain.journal.Journal
import dev.valkhes.mealplanner.domain.journal.Meal
import dev.valkhes.mealplanner.domain.shared.DishId
import kotlinx.datetime.LocalDate

data class DishSummary(
    val dish: Dish,
    val habit: Habit,
)

data class DishDetail(
    val dish: Dish,
    val habit: Habit,
    val meals: List<Meal>,
)

data class MealEntry(
    val meal: Meal,
    val dish: Dish,
)

/** Read-side views of what the household eats. Pure: handed the cookbook and the journal. */
object HabitViews {
    fun summaries(
        cookbook: Cookbook,
        journal: Journal,
    ): List<DishSummary> {
        val datesByDish: Map<DishId, List<LocalDate>> = journal.occasions().groupBy({ it.dishId }, { it.date })
        return cookbook.all
            .map { DishSummary(it, Habit.of(datesByDish[it.id].orEmpty())) }
            .sortedWith(
                compareByDescending<DishSummary> { it.habit.count }
                    .thenByDescending { it.habit.last }
                    .thenBy { it.dish.name.key },
            )
    }

    fun detail(
        cookbook: Cookbook,
        journal: Journal,
        id: DishId,
    ): DishDetail? {
        val dish = cookbook[id] ?: return null
        val dates = journal.occasions().filter { it.dishId == id }.map { it.date }
        return DishDetail(dish, Habit.of(dates), journal.active.filter { it.dishId == id })
    }

    fun meals(
        cookbook: Cookbook,
        journal: Journal,
    ): List<MealEntry> = journal.active.mapNotNull { meal -> cookbook[meal.dishId]?.let { MealEntry(meal, it) } }

    /**
     * The dishes most worth suggesting today: the most overdue against their own rhythm first, then
     * the most eaten. Dishes eaten today are left out.
     */
    fun suggestions(
        cookbook: Cookbook,
        journal: Journal,
        today: LocalDate,
        limit: Int,
    ): List<DishSummary> =
        summaries(cookbook, journal)
            .filter { it.habit.count > 0 && it.habit.last != today }
            .sortedWith(
                compareByDescending<DishSummary> { it.habit.dueRatio(today) ?: -1.0 }
                    .thenByDescending { it.habit.count }
                    .thenByDescending { it.habit.last },
            ).take(limit)
}

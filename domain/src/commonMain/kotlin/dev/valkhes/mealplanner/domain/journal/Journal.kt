package dev.valkhes.mealplanner.domain.journal

import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.datetime.LocalDate

data class Meal(
    val id: MealId,
    val dishId: DishId,
    val date: LocalDate,
    val slot: MealSlot,
    val retracted: Boolean = false,
)

/**
 * The same dish, on the same day, at the same slot: one occasion. Two phones that both logged
 * tonight's dinner logged one meal, so frequencies count occasions, never logs.
 */
data class Occasion(
    val dishId: DishId,
    val date: LocalDate,
    val slot: MealSlot,
)

/** Every meal the household logged. Immutable: [apply] returns the next state. */
class Journal private constructor(
    private val meals: Map<MealId, Meal>,
) {
    operator fun get(id: MealId): Meal? = meals[id]

    /** Meals not retracted, newest first. */
    val active: List<Meal> get() = meals.values.filterNot { it.retracted }.sortedWith(NEWEST_FIRST)

    fun occasions(): Set<Occasion> = active.mapTo(LinkedHashSet()) { Occasion(it.dishId, it.date, it.slot) }

    fun log(
        id: MealId,
        dishId: DishId,
        date: LocalDate,
        slot: MealSlot,
    ): Decision<JournalEvent> = Decision.Accepted(listOf(MealLogged(id, dishId, date, slot)))

    fun retract(id: MealId): Decision<JournalEvent> {
        val meal = meals[id] ?: return Decision.Rejected(DomainError.MEAL_UNKNOWN)
        return if (meal.retracted) {
            Decision.Rejected(DomainError.MEAL_ALREADY_RETRACTED)
        } else {
            Decision.Accepted(listOf(MealRetracted(id)))
        }
    }

    /** Folds one recorded event in. Events are facts: an unknown meal's retraction changes nothing. */
    fun apply(event: JournalEvent): Journal =
        when (event) {
            is MealLogged -> Journal(meals + (event.mealId to Meal(event.mealId, event.dishId, event.date, event.slot, meals[event.mealId]?.retracted == true)))
            is MealRetracted -> {
                val meal = meals[event.mealId]
                if (meal == null) this else Journal(meals + (event.mealId to meal.copy(retracted = true)))
            }
        }

    companion object {
        val EMPTY: Journal = Journal(emptyMap())

        private val NEWEST_FIRST: Comparator<Meal> =
            compareByDescending<Meal> { it.date }.thenByDescending { it.slot.ordinal }.thenByDescending { it.id.value }
    }
}

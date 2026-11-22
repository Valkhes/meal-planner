package dev.valkhes.mealplanner.domain.journal

import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainEvent
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.datetime.LocalDate

sealed interface JournalEvent : DomainEvent {
    val mealId: MealId
}

data class MealLogged(
    override val mealId: MealId,
    val dishId: DishId,
    val date: LocalDate,
    val slot: MealSlot,
) : JournalEvent

/** A meal logged by mistake. The log stays; this fact cancels it. */
data class MealRetracted(
    override val mealId: MealId,
) : JournalEvent

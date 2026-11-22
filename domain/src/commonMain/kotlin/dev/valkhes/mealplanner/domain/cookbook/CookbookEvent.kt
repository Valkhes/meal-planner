package dev.valkhes.mealplanner.domain.cookbook

import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainEvent

sealed interface CookbookEvent : DomainEvent {
    val dishId: DishId
}

data class DishCreated(
    override val dishId: DishId,
    val name: DishName,
) : CookbookEvent

data class DishRenamed(
    override val dishId: DishId,
    val name: DishName,
) : CookbookEvent

/** Replaces the whole recipe: the latest edit wins (README, sync rules). */
data class RecipeEdited(
    override val dishId: DishId,
    val recipe: Recipe,
) : CookbookEvent

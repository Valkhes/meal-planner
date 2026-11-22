package dev.valkhes.mealplanner.domain.cookbook

import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError

data class Dish(
    val id: DishId,
    val name: DishName,
    val recipe: Recipe = Recipe(),
)

/** The household's dishes. Immutable: [apply] returns the next state, decisions return events. */
class Cookbook private constructor(
    private val dishes: Map<DishId, Dish>,
) {
    val all: Collection<Dish> get() = dishes.values

    operator fun get(id: DishId): Dish? = dishes[id]

    fun findByName(name: DishName): Dish? = dishes.values.firstOrNull { it.name.key == name.key }

    fun create(
        id: DishId,
        name: DishName,
    ): Decision<CookbookEvent> =
        when {
            findByName(name) != null -> Decision.Rejected(DomainError.DISH_NAME_TAKEN)
            else -> Decision.Accepted(listOf(DishCreated(id, name)))
        }

    fun rename(
        id: DishId,
        name: DishName,
    ): Decision<CookbookEvent> {
        val dish = dishes[id] ?: return Decision.Rejected(DomainError.DISH_UNKNOWN)
        val other = findByName(name)
        return when {
            other != null && other.id != id -> Decision.Rejected(DomainError.DISH_NAME_TAKEN)
            dish.name == name -> Decision.Accepted(emptyList())
            else -> Decision.Accepted(listOf(DishRenamed(id, name)))
        }
    }

    fun editRecipe(
        id: DishId,
        recipe: Recipe,
    ): Decision<CookbookEvent> {
        val dish = dishes[id] ?: return Decision.Rejected(DomainError.DISH_UNKNOWN)
        return when {
            !recipe.isValid() -> Decision.Rejected(DomainError.RECIPE_INVALID)
            dish.recipe == recipe -> Decision.Accepted(emptyList())
            else -> Decision.Accepted(listOf(RecipeEdited(id, recipe)))
        }
    }

    /**
     * Folds one recorded event in. It never refuses: events are facts, possibly from the other phone,
     * so an edit of a dish this phone has not seen yet is kept for when the dish arrives.
     */
    fun apply(event: CookbookEvent): Cookbook =
        when (event) {
            is DishCreated -> Cookbook(dishes + (event.dishId to (dishes[event.dishId]?.copy(name = event.name) ?: Dish(event.dishId, event.name))))
            is DishRenamed -> update(event.dishId) { it.copy(name = event.name) }
            is RecipeEdited -> update(event.dishId) { it.copy(recipe = event.recipe) }
        }

    private fun update(
        id: DishId,
        change: (Dish) -> Dish,
    ): Cookbook {
        val dish = dishes[id] ?: return this
        return Cookbook(dishes + (id to change(dish)))
    }

    companion object {
        val EMPTY: Cookbook = Cookbook(emptyMap())
    }
}

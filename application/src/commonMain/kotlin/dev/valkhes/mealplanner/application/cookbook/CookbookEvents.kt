package dev.valkhes.mealplanner.application.cookbook

import dev.valkhes.mealplanner.application.events.EventType
import dev.valkhes.mealplanner.application.events.intOrNull
import dev.valkhes.mealplanner.application.events.json
import dev.valkhes.mealplanner.application.events.objects
import dev.valkhes.mealplanner.application.events.string
import dev.valkhes.mealplanner.application.events.stringOrNull
import dev.valkhes.mealplanner.application.events.strings
import dev.valkhes.mealplanner.domain.cookbook.DishCreated
import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.cookbook.DishRenamed
import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.cookbook.RecipeEdited
import dev.valkhes.mealplanner.domain.shared.DishId

/** The stored schema of the cookbook's events. Released versions are frozen: see EventType. */
object CookbookEvents {
    val dishCreated: EventType<DishCreated> =
        EventType(
            name = "DishCreated",
            eventClass = DishCreated::class,
            version = 1,
            encode = { json("dishId" to it.dishId.value, "name" to it.name.text) },
            decode = { DishCreated(DishId(it.string("dishId")), DishName.trusted(it.string("name"))) },
        )

    val dishRenamed: EventType<DishRenamed> =
        EventType(
            name = "DishRenamed",
            eventClass = DishRenamed::class,
            version = 1,
            encode = { json("dishId" to it.dishId.value, "name" to it.name.text) },
            decode = { DishRenamed(DishId(it.string("dishId")), DishName.trusted(it.string("name"))) },
        )

    val recipeEdited: EventType<RecipeEdited> =
        EventType(
            name = "RecipeEdited",
            eventClass = RecipeEdited::class,
            version = 1,
            encode = {
                json(
                    "dishId" to it.dishId.value,
                    "ingredients" to it.recipe.ingredients.map { i -> json("name" to i.name, "quantity" to i.quantity) },
                    "steps" to it.recipe.steps,
                    "minutes" to it.recipe.minutes,
                    "notes" to it.recipe.notes,
                )
            },
            decode = {
                RecipeEdited(
                    DishId(it.string("dishId")),
                    Recipe(
                        ingredients = it.objects("ingredients").map { i -> Ingredient(i.string("name"), i.stringOrNull("quantity")) },
                        steps = it.strings("steps"),
                        minutes = it.intOrNull("minutes"),
                        notes = it.stringOrNull("notes"),
                    ),
                )
            },
        )

    val types: List<EventType<*>> = listOf(dishCreated, dishRenamed, recipeEdited)
}

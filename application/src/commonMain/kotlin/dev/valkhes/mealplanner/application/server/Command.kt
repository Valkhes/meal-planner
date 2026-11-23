package dev.valkhes.mealplanner.application.server

import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.planning.AbsenceId
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.datetime.LocalDate

/**
 * Every change goes through a command: the UI sends them today, an AI interpreter or an MCP tool will
 * send the same ones later (README, constraint 6). Skill `new-command` adds one.
 */
sealed interface Command

/** A dish the household already has, or a name that becomes one if no dish has it yet. */
sealed interface DishRef {
    data class Existing(
        val id: DishId,
    ) : DishRef

    data class Named(
        val name: String,
    ) : DishRef
}

data class LogMeal(
    val dish: DishRef,
    val date: LocalDate,
    val slot: MealSlot,
) : Command

data class RetractMeal(
    val mealId: MealId,
) : Command

data class CreateDish(
    val name: String,
    val recipe: Recipe = Recipe(),
) : Command

data class RenameDish(
    val dishId: DishId,
    val name: String,
) : Command

data class EditRecipe(
    val dishId: DishId,
    val recipe: Recipe,
) : Command

/** The household is away from [from] to [to], both included: no reminder, and the rhythm skips those days. */
data class SetAbsence(
    val from: LocalDate,
    val to: LocalDate,
) : Command

data class RemoveAbsence(
    val absenceId: AbsenceId,
) : Command

sealed interface CommandResult {
    /** What the command created or touched, so the UI can show or undo it. */
    data class Done(
        val dishId: DishId? = null,
        val mealId: MealId? = null,
        val absenceId: AbsenceId? = null,
    ) : CommandResult

    data class Refused(
        val error: DomainError,
    ) : CommandResult
}

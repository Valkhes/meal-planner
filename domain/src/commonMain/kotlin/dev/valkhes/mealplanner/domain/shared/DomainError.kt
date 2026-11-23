package dev.valkhes.mealplanner.domain.shared

/** Why a command was refused. [code] is stable: the UI maps it to a translated message. */
enum class DomainError(
    val code: String,
) {
    DISH_NAME_EMPTY("dish_name_empty"),
    DISH_NAME_TOO_LONG("dish_name_too_long"),
    DISH_NAME_TAKEN("dish_name_taken"),
    DISH_UNKNOWN("dish_unknown"),
    MEAL_UNKNOWN("meal_unknown"),
    MEAL_ALREADY_RETRACTED("meal_already_retracted"),
    RECIPE_INVALID("recipe_invalid"),
    ABSENCE_INVALID("absence_invalid"),
    ABSENCE_UNKNOWN("absence_unknown"),
}

/** The result of a decision: the events to record, or why nothing happens. */
sealed interface Decision<out E : DomainEvent> {
    data class Accepted<E : DomainEvent>(
        val events: List<E>,
    ) : Decision<E>

    data class Rejected(
        val error: DomainError,
    ) : Decision<Nothing>
}

package dev.valkhes.mealplanner.domain.cookbook

import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.textKey

/** A dish name as the household writes it, trimmed. Two names with the same [key] are the same dish. */
class DishName private constructor(
    val text: String,
) {
    val key: String = textKey(text)

    override fun equals(other: Any?): Boolean = other is DishName && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = text

    companion object {
        const val MAX_LENGTH: Int = 80

        /** The name, or the reason it cannot be one. */
        fun parse(raw: String): Pair<DishName?, DomainError?> {
            val text = raw.trim().replace(Regex("\\s+"), " ")
            return when {
                text.isEmpty() -> null to DomainError.DISH_NAME_EMPTY
                text.length > MAX_LENGTH -> null to DomainError.DISH_NAME_TOO_LONG
                else -> DishName(text) to null
            }
        }

        /** For names already validated, such as those read back from recorded events. */
        fun trusted(text: String): DishName = DishName(text)
    }
}

package dev.valkhes.mealplanner.domain.shared

import kotlin.jvm.JvmInline

/** Identifies a dish forever: saved events reference it, so it never changes, even when the dish is renamed. */
@JvmInline
value class DishId(
    val value: String,
)

/** Identifies one logged meal, so it can be retracted. */
@JvmInline
value class MealId(
    val value: String,
)

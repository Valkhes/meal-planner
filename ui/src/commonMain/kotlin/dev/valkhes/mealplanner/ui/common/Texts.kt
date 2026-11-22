package dev.valkhes.mealplanner.ui.common

import androidx.compose.runtime.Composable
import dev.valkhes.mealplanner.domain.habits.Habit
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.ui.generated.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** "today", "yesterday", "3 days ago", then "Nov 12" / "12 nov.": relative while it reads better. */
@Composable
fun dateText(
    date: LocalDate,
    today: LocalDate,
): String {
    val days = date.daysUntil(today)
    return when {
        days == 0 -> stringResource(Res.string.date_today)
        days == 1 -> stringResource(Res.string.date_yesterday)
        days in 2..6 -> pluralStringResource(Res.plurals.date_days_ago, days, days)
        else -> stringResource(Res.string.date_day_month, date.day, stringArrayResource(Res.array.months_short)[date.month.ordinal])
    }
}

@Composable
fun slotText(slot: MealSlot): String =
    stringResource(
        when (slot) {
            MealSlot.LUNCH -> Res.string.slot_lunch
            MealSlot.DINNER -> Res.string.slot_dinner
            MealSlot.OTHER -> Res.string.slot_other
        },
    )

/** "4 times · last yesterday · about every 9 days". */
@Composable
fun habitText(
    habit: Habit,
    today: LocalDate,
): String {
    if (habit.count == 0 || habit.last == null) return stringResource(Res.string.never_eaten)
    val parts = mutableListOf(pluralStringResource(Res.plurals.times_count, habit.count, habit.count))
    parts += stringResource(Res.string.last_eaten, dateText(habit.last!!, today))
    habit.typicalIntervalDays?.let { parts += pluralStringResource(Res.plurals.every_days, it, it) }
    return parts.joinToString(" · ")
}

@Composable
fun errorText(error: DomainError): String = stringResource(ERRORS.getValue(error))

private val ERRORS: Map<DomainError, StringResource> =
    mapOf(
        DomainError.DISH_NAME_EMPTY to Res.string.error_dish_name_empty,
        DomainError.DISH_NAME_TOO_LONG to Res.string.error_dish_name_too_long,
        DomainError.DISH_NAME_TAKEN to Res.string.error_dish_name_taken,
        DomainError.DISH_UNKNOWN to Res.string.error_dish_unknown,
        DomainError.MEAL_UNKNOWN to Res.string.error_meal_unknown,
        DomainError.MEAL_ALREADY_RETRACTED to Res.string.error_meal_already_retracted,
        DomainError.RECIPE_INVALID to Res.string.error_recipe_invalid,
    )

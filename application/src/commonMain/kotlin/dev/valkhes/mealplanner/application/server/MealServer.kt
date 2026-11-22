package dev.valkhes.mealplanner.application.server

import dev.valkhes.mealplanner.application.habits.DishDetail
import dev.valkhes.mealplanner.application.habits.DishSummary
import dev.valkhes.mealplanner.application.habits.MealEntry
import dev.valkhes.mealplanner.domain.shared.DishId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * The application's single entry point: commands in, read models out. The UI talks only to this;
 * an AI interpreter or an MCP server will use the same calls as tools. Today: [LocalMealServer].
 */
interface MealServer {
    /** Goes up after every change, so screens know when to read again. */
    val revision: StateFlow<Long>

    suspend fun send(command: Command): CommandResult

    suspend fun dishes(): List<DishSummary>

    suspend fun dish(id: DishId): DishDetail?

    suspend fun recentMeals(limit: Int): List<MealEntry>

    suspend fun mealsOn(date: LocalDate): List<MealEntry>

    suspend fun suggestions(limit: Int): List<DishSummary>
}

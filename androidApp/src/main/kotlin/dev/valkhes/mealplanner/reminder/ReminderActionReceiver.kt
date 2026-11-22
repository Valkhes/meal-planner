package dev.valkhes.mealplanner.reminder

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.valkhes.mealplanner.MealPlannerApplication
import dev.valkhes.mealplanner.R
import dev.valkhes.mealplanner.application.assistant.InterpretContext
import dev.valkhes.mealplanner.application.assistant.Interpretation
import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Answers to the evening reminder. Typing a dish in the notification is an explicit answer, so it is
 * logged right away and confirmed in the notification; anything not understood opens the app.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val container = (context.applicationContext as MealPlannerApplication).container
        scope.launch {
            try {
                val date = intent.getStringExtra(EXTRA_DATE)?.let { LocalDate.parse(it) }
                when (intent.action) {
                    ACTION_LOG -> {
                        val id = intent.getStringExtra(EXTRA_DISH_ID) ?: return@launch
                        val result = container.server().send(LogMeal(DishRef.Existing(DishId(id)), date ?: return@launch, MealSlot.DINNER))
                        confirm(context, result, intent.getStringExtra(EXTRA_DISH_NAME).orEmpty())
                    }
                    ACTION_OTHER -> {
                        val text =
                            RemoteInput
                                .getResultsFromIntent(intent)
                                ?.getCharSequence(KEY_TEXT)
                                ?.toString()
                                .orEmpty()
                        val server = container.server()
                        val interpretContext = InterpretContext(server.dishes().map { it.dish }, date ?: return@launch, hour = 21)
                        when (val meal = container.interpreter.interpret(text, interpretContext)) {
                            is Interpretation.Meal -> confirm(context, server.send(meal.toCommand()), meal.dishName)
                            else -> EveningReminder.confirm(context, context.getString(R.string.reminder_not_understood), openOnTap = true)
                        }
                    }
                    ACTION_NOT_HOME -> EveningReminder.cancel(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun confirm(
        context: Context,
        result: CommandResult,
        dishName: String,
    ) {
        if (result is CommandResult.Done) {
            EveningReminder.confirm(context, context.getString(R.string.reminder_logged, dishName), openOnTap = false)
        } else {
            EveningReminder.confirm(context, context.getString(R.string.reminder_not_understood), openOnTap = true)
        }
    }

    companion object {
        const val KEY_TEXT: String = "text"
        private const val ACTION_LOG = "dev.valkhes.mealplanner.reminder.LOG"
        private const val ACTION_OTHER = "dev.valkhes.mealplanner.reminder.OTHER"
        private const val ACTION_NOT_HOME = "dev.valkhes.mealplanner.reminder.NOT_HOME"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_DISH_ID = "dish_id"
        private const val EXTRA_DISH_NAME = "dish_name"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun logDish(
            context: Context,
            date: String,
            dishId: String,
            dishName: String,
        ): Intent =
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_LOG)
                .putExtra(EXTRA_DATE, date)
                .putExtra(EXTRA_DISH_ID, dishId)
                .putExtra(EXTRA_DISH_NAME, dishName)
                .setIdentifier(dishId)

        fun other(
            context: Context,
            date: String,
        ): Intent = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_OTHER).putExtra(EXTRA_DATE, date)

        fun notHome(context: Context): Intent = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_NOT_HOME)
    }
}

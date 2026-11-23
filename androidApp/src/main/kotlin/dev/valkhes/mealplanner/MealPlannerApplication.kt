package dev.valkhes.mealplanner

import android.app.Application
import dev.valkhes.mealplanner.reminder.EveningReminder

class MealPlannerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        EveningReminder.schedule(this, container.reminder.current(), replace = false)
    }
}

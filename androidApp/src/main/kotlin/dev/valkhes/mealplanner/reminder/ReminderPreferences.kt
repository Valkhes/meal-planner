package dev.valkhes.mealplanner.reminder

import android.content.Context
import dev.valkhes.mealplanner.ui.ReminderControls
import dev.valkhes.mealplanner.ui.ReminderSetting

/** The evening reminder's settings, private to this phone, and its schedule kept in step with them. */
class ReminderPreferences(
    private val context: Context,
) : ReminderControls {
    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    override fun current(): ReminderSetting =
        ReminderSetting(
            enabled = prefs.getBoolean(ENABLED, true),
            hour = prefs.getInt(HOUR, DEFAULT_HOUR),
            minute = prefs.getInt(MINUTE, 0),
        )

    override fun update(setting: ReminderSetting) {
        prefs
            .edit()
            .putBoolean(ENABLED, setting.enabled)
            .putInt(HOUR, setting.hour.coerceIn(0, 23))
            .putInt(MINUTE, setting.minute.coerceIn(0, 59))
            .apply()
        EveningReminder.schedule(context, current(), replace = true)
    }

    private companion object {
        const val ENABLED = "enabled"
        const val HOUR = "hour"
        const val MINUTE = "minute"
        const val DEFAULT_HOUR = 21
    }
}

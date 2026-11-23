package dev.valkhes.mealplanner

import android.content.Context
import dev.valkhes.mealplanner.application.assistant.Interpreter
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.server.MealServer
import dev.valkhes.mealplanner.application.shared.Clock
import dev.valkhes.mealplanner.application.shared.Device
import dev.valkhes.mealplanner.application.shared.UuidV7
import dev.valkhes.mealplanner.infrastructure.assistant.RuleInterpreter
import dev.valkhes.mealplanner.infrastructure.storage.SqliteEventStore
import dev.valkhes.mealplanner.infrastructure.system.SystemClock
import dev.valkhes.mealplanner.reminder.ReminderPreferences
import dev.valkhes.mealplanner.ui.AppDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/** The composition root: the one place that picks the adapters behind every port. */
class AppContainer(
    private val context: Context,
) {
    val clock: Clock = SystemClock
    val interpreter: Interpreter = RuleInterpreter()
    val reminder: ReminderPreferences = ReminderPreferences(context)
    private val ids = UuidV7(clock, SecureRandom().asKotlinRandom())
    private val mutex = Mutex()
    private var server: MealServer? = null

    /** Opened once, on first use: replays the log into memory. */
    suspend fun server(): MealServer =
        mutex.withLock {
            server ?: LocalMealServer
                .open(
                    store = SqliteEventStore(File(context.filesDir, "events.db").path, Dispatchers.IO),
                    clock = clock,
                    ids = ids,
                    device = device(),
                ).also { server = it }
        }

    suspend fun dependencies(): AppDependencies = AppDependencies(server(), interpreter, clock, reminder)

    /** The household and phone ids, created at first launch and kept in private preferences. */
    private fun device(): Device {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        val household = prefs.getString(HOUSEHOLD, null) ?: ids.newId().also { prefs.edit().putString(HOUSEHOLD, it).apply() }
        val device = prefs.getString(DEVICE, null) ?: ids.newId().also { prefs.edit().putString(DEVICE, it).apply() }
        return Device(household, device)
    }

    private companion object {
        const val HOUSEHOLD = "household_id"
        const val DEVICE = "device_id"
    }
}

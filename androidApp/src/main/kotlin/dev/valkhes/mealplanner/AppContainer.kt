package dev.valkhes.mealplanner

import android.content.Context
import dev.valkhes.mealplanner.application.assistant.Interpreter
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.shared.Clock
import dev.valkhes.mealplanner.application.shared.UuidV7
import dev.valkhes.mealplanner.infrastructure.assistant.RuleInterpreter
import dev.valkhes.mealplanner.infrastructure.storage.SqliteEventStore
import dev.valkhes.mealplanner.infrastructure.system.SystemClock
import dev.valkhes.mealplanner.reminder.ReminderPreferences
import dev.valkhes.mealplanner.sync.Household
import dev.valkhes.mealplanner.sync.LanSync
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
    private val household = Household(context, ids)
    val sync: LanSync by lazy { LanSync(context, household, ::server) }
    private val mutex = Mutex()
    private var server: LocalMealServer? = null

    /** Opened once, on first use: replays the log into memory. */
    suspend fun server(): LocalMealServer =
        mutex.withLock {
            server ?: LocalMealServer
                .open(
                    store = SqliteEventStore(File(context.filesDir, "events.db").path, Dispatchers.IO),
                    clock = clock,
                    ids = ids,
                    device = household,
                ).also { server = it }
        }

    suspend fun dependencies(): AppDependencies = AppDependencies(server(), interpreter, clock, reminder, sync)
}

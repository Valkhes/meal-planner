package dev.valkhes.mealplanner.infrastructure.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.valkhes.mealplanner.application.events.EventEnvelope
import dev.valkhes.mealplanner.application.events.EventStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * The event log in one SQLite table, on the phone. The table's shape is the envelope's, which never
 * changes; [STORE_VERSION] exists for the day it must, and would get a migration then.
 */
class SqliteEventStore(
    private val path: String,
    private val dispatcher: CoroutineDispatcher,
) : EventStore {
    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null

    override suspend fun append(envelopes: List<EventEnvelope>) {
        if (envelopes.isEmpty()) return
        use { db ->
            db.execSQL("BEGIN IMMEDIATE")
            try {
                db.prepare(INSERT).use { statement ->
                    for (e in envelopes) {
                        statement.bindText(1, e.id)
                        statement.bindText(2, e.type)
                        statement.bindLong(3, e.version.toLong())
                        statement.bindText(4, e.householdId)
                        statement.bindText(5, e.deviceId)
                        statement.bindLong(6, e.occurredAt.toEpochMilliseconds())
                        statement.bindText(7, e.timeZone)
                        statement.bindLong(8, e.recordedAt.toEpochMilliseconds())
                        statement.bindText(9, e.payload)
                        statement.step()
                        statement.reset()
                    }
                }
                db.execSQL("COMMIT")
            } catch (failure: Throwable) {
                db.execSQL("ROLLBACK")
                throw failure
            }
        }
    }

    override suspend fun all(): List<EventEnvelope> =
        use { db ->
            db.prepare(SELECT_ALL).use { statement ->
                buildList { while (statement.step()) add(statement.toEnvelope()) }
            }
        }

    fun close() {
        connection?.close()
        connection = null
    }

    private suspend fun <T> use(block: (SQLiteConnection) -> T): T =
        withContext(dispatcher) {
            mutex.withLock { block(connection ?: open().also { connection = it }) }
        }

    private fun open(): SQLiteConnection {
        val db = BundledSQLiteDriver().open(path)
        db.execSQL("PRAGMA journal_mode=WAL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS events (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                version INTEGER NOT NULL,
                household_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                time_zone TEXT NOT NULL,
                recorded_at INTEGER NOT NULL,
                payload TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS events_by_record ON events (recorded_at, id)")
        db.execSQL("PRAGMA user_version = $STORE_VERSION")
        return db
    }

    private fun SQLiteStatement.toEnvelope(): EventEnvelope =
        EventEnvelope(
            id = getText(0),
            type = getText(1),
            version = getLong(2).toInt(),
            householdId = getText(3),
            deviceId = getText(4),
            occurredAt = Instant.fromEpochMilliseconds(getLong(5)),
            timeZone = getText(6),
            recordedAt = Instant.fromEpochMilliseconds(getLong(7)),
            payload = getText(8),
        )

    private companion object {
        const val STORE_VERSION: Int = 1

        const val INSERT: String =
            "INSERT OR IGNORE INTO events (id, type, version, household_id, device_id, occurred_at, time_zone, recorded_at, payload) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"

        const val SELECT_ALL: String =
            "SELECT id, type, version, household_id, device_id, occurred_at, time_zone, recorded_at, payload FROM events ORDER BY recorded_at, id"
    }
}

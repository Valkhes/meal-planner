package dev.valkhes.mealplanner.infrastructure

import dev.valkhes.mealplanner.application.events.EventEnvelope
import dev.valkhes.mealplanner.infrastructure.storage.SqliteEventStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SqliteEventStoreTest {
    private fun envelope(
        id: String,
        recordedMs: Long,
    ): EventEnvelope =
        EventEnvelope(
            id,
            "MealLogged",
            1,
            "h",
            "d",
            Instant.fromEpochMilliseconds(1_000),
            "Europe/Paris",
            Instant.fromEpochMilliseconds(recordedMs),
            """{"mealId":"$id"}""",
        )

    @Test
    fun envelopes_survive_a_reopen_in_recorded_order_and_duplicates_are_ignored() =
        runTest {
            val path = "build/tmp/test-events-${kotlin.random.Random.nextLong()}.db"
            val store = SqliteEventStore(path, Dispatchers.Default)
            store.append(listOf(envelope("b", 2_000), envelope("a", 1_000)))
            store.append(listOf(envelope("a", 1_000), envelope("c", 2_000)))
            store.close()

            val reopened = SqliteEventStore(path, Dispatchers.Default)
            assertEquals(listOf("a", "b", "c"), reopened.all().map { it.id })
            assertEquals(envelope("b", 2_000), reopened.all()[1])
            reopened.close()
        }
}

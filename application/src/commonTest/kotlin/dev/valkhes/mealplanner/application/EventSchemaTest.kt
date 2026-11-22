package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.application.events.EventEnvelope
import dev.valkhes.mealplanner.application.events.EventSchema
import dev.valkhes.mealplanner.application.events.ReadEvent
import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.domain.journal.MealSlot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class EventSchemaTest {
    private val schema = EventSchema.CURRENT

    /** Every version of every event type ever released must still read, exactly as recorded the day it shipped. */
    @Test
    fun every_released_version_has_a_golden_that_still_decodes() {
        for (type in schema.types) {
            for (version in 1..type.version) {
                val golden =
                    EventGoldens.ALL.firstOrNull { it.type == type.name && it.version == version }
                        ?: fail("${type.name} v$version has no golden in EventGoldens: add one (skill new-event-version)")
                val payload = Json.parseToJsonElement(golden.payload).jsonObject
                assertEquals(golden.expected, schema.decode(type, payload, version), "${type.name} v$version")
            }
        }
    }

    @Test
    fun goldens_only_name_released_versions() {
        for (golden in EventGoldens.ALL) {
            val type = schema.types.firstOrNull { it.name == golden.type } ?: fail("golden for unknown type ${golden.type}")
            assertTrue(golden.version in 1..type.version, "${golden.type} v${golden.version} was never released")
        }
    }

    @Test
    fun current_events_encode_to_what_they_decode_from() {
        for (golden in EventGoldens.ALL.filter { g -> schema.types.single { it.name == g.type }.version == g.version }) {
            val (type, payload) = schema.encode(golden.expected)
            assertEquals(golden.expected, schema.decode(type, Json.parseToJsonElement(payload).jsonObject, type.version))
        }
    }

    @Test
    fun an_event_from_a_newer_app_is_kept_and_skipped() =
        runTest {
            val store = InMemoryEventStore()
            val server = openServer(store)
            server.send(LogMeal(DishRef.Named("Curry"), LocalDate(2026, 11, 20), MealSlot.DINNER))
            val newer = store.envelopes.first().copy(id = "id-9999", type = "TasteNoted", version = 1, payload = """{"x":1}""")
            val newerVersion = store.envelopes.last().copy(id = "id-9998", version = 99, payload = """{"y":2}""")
            store.append(listOf(newer, newerVersion))

            assertIs<ReadEvent.Unknown>(schema.read(newer))
            assertIs<ReadEvent.Unknown>(schema.read(newerVersion))
            val reopened = openServer(store)
            assertEquals(
                1,
                reopened
                    .dishes()
                    .single()
                    .habit.count,
            )
            assertEquals(4, store.all().size)
        }

    @Test
    fun envelopes_round_trip_through_the_schema() {
        val golden = EventGoldens.ALL.first { it.type == "RecipeEdited" }
        val (type, payload) = schema.encode(golden.expected)
        val envelope =
            EventEnvelope("id", type.name, type.version, "h", "d", FakeClock().now(), "Europe/Paris", FakeClock().now(), payload)
        assertEquals(golden.expected, (schema.read(envelope) as ReadEvent.Known).event)
    }
}

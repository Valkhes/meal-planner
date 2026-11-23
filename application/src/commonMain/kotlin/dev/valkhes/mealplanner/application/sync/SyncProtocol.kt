package dev.valkhes.mealplanner.application.sync

import dev.valkhes.mealplanner.application.events.EventEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Instant

/**
 * What two phones of a household say to each other. The exchange is symmetric: each sends a
 * [Hello] listing the event ids it has, then the [Events] the other lacks. Merging is a union of
 * events, so the order of arrival never matters and syncing twice changes nothing.
 *
 * Whatever carries the messages (local network, Bluetooth, a file) only moves bytes: its adapter
 * authenticates the other phone and encrypts with the household key.
 */
sealed interface SyncMessage {
    data class Hello(
        val protocol: Int,
        val deviceId: String,
        val ids: Set<String>,
    ) : SyncMessage

    data class Events(
        val envelopes: List<EventEnvelope>,
    ) : SyncMessage
}

/** A duplex channel to the other phone, already authenticated and encrypted by its adapter. */
interface SyncChannel {
    suspend fun send(message: SyncMessage)

    suspend fun receive(): SyncMessage
}

/** What a sync did, to show the user. */
data class SyncReport(
    val sent: Int,
    val received: Int,
)

/** The other phone speaks a protocol this app does not know: one of the two needs an update. */
class SyncProtocolMismatch(
    val theirs: Int,
) : Exception("the other phone speaks sync protocol $theirs, this one ${SyncCodec.PROTOCOL}")

/** The messages as UTF-8 JSON, the same on every transport. */
object SyncCodec {
    const val PROTOCOL: Int = 1

    fun encode(message: SyncMessage): String =
        when (message) {
            is SyncMessage.Hello ->
                JsonObject(
                    mapOf(
                        "kind" to JsonPrimitive("hello"),
                        "protocol" to JsonPrimitive(message.protocol),
                        "deviceId" to JsonPrimitive(message.deviceId),
                        "ids" to JsonArray(message.ids.sorted().map { JsonPrimitive(it) }),
                    ),
                )
            is SyncMessage.Events ->
                JsonObject(mapOf("kind" to JsonPrimitive("events"), "events" to JsonArray(message.envelopes.map { envelope(it) })))
        }.toString()

    fun decode(text: String): SyncMessage {
        val json = Json.parseToJsonElement(text).jsonObject
        return when (val kind = json.getValue("kind").jsonPrimitive.content) {
            "hello" ->
                SyncMessage.Hello(
                    protocol = json.getValue("protocol").jsonPrimitive.int,
                    deviceId = json.getValue("deviceId").jsonPrimitive.content,
                    ids = json.getValue("ids").jsonArray.mapTo(HashSet()) { it.jsonPrimitive.content },
                )
            "events" -> SyncMessage.Events(json.getValue("events").jsonArray.map { envelope(it.jsonObject) })
            else -> throw IllegalArgumentException("unknown sync message $kind")
        }
    }

    private fun envelope(e: EventEnvelope): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(e.id),
                "type" to JsonPrimitive(e.type),
                "version" to JsonPrimitive(e.version),
                "householdId" to JsonPrimitive(e.householdId),
                "deviceId" to JsonPrimitive(e.deviceId),
                "occurredAt" to JsonPrimitive(e.occurredAt.toEpochMilliseconds()),
                "timeZone" to JsonPrimitive(e.timeZone),
                "recordedAt" to JsonPrimitive(e.recordedAt.toEpochMilliseconds()),
                "payload" to JsonPrimitive(e.payload),
            ),
        )

    private fun envelope(json: JsonObject): EventEnvelope {
        fun text(key: String): String = json.getValue(key).jsonPrimitive.content
        return EventEnvelope(
            id = text("id"),
            type = text("type"),
            version = json.getValue("version").jsonPrimitive.int,
            householdId = text("householdId"),
            deviceId = text("deviceId"),
            occurredAt = Instant.fromEpochMilliseconds(json.getValue("occurredAt").jsonPrimitive.long),
            timeZone = text("timeZone"),
            recordedAt = Instant.fromEpochMilliseconds(json.getValue("recordedAt").jsonPrimitive.long),
            payload = text("payload"),
        )
    }
}

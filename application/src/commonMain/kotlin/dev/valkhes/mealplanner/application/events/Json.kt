package dev.valkhes.mealplanner.application.events

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Small readers for payloads written by hand-coded codecs: explicit, so every field's name is visible. */
internal fun JsonObject.string(key: String): String = get(key)?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("missing field $key")

internal fun JsonObject.stringOrNull(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.intOrNull(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

internal fun JsonObject.objects(key: String): List<JsonObject> = get(key)?.jsonArray?.map { it.jsonObject } ?: emptyList()

internal fun JsonObject.strings(key: String): List<String> = get(key)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

internal fun json(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(fields.filter { it.second != null }.associate { (k, v) -> k to element(v) })

private fun element(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { element(it) })
        else -> throw IllegalArgumentException("not a JSON value: $value")
    }

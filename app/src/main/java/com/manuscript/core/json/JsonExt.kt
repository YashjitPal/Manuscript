package com.manuscript.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

val NotionJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

fun JsonObject.o(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.a(key: String): JsonArray? = this[key] as? JsonArray
fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
fun JsonObject.b(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.booleanOrNull
fun JsonObject.d(key: String): Double? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
fun JsonObject.i(key: String): Int? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull
fun JsonObject.l(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.longOrNull
fun JsonObject.has(key: String): Boolean = this[key] != null && this[key] !is JsonNull

fun JsonArray.objs(): List<JsonObject> = mapNotNull { it as? JsonObject }
fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray
fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJson() })
    is Iterable<*> -> JsonArray(map { it.toJson() })
    is Array<*> -> JsonArray(map { it.toJson() })
    else -> JsonPrimitive(toString())
}

/** Builds a JSON object, skipping entries whose value is [Skip]. Explicit nulls are kept as JSON null. */
fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.filter { it.second !== Skip }.associate { (k, v) -> k to v.toJson() })

fun arr(items: Iterable<Any?>): JsonArray = JsonArray(items.map { it.toJson() })

object Skip

fun JsonObject.plus(key: String, value: Any?): JsonObject = JsonObject(this + (key to value.toJson()))
fun JsonObject.minusKey(key: String): JsonObject = JsonObject(this - key)

fun parseObject(text: String): JsonObject = NotionJson.parseToJsonElement(text) as JsonObject
fun JsonElement.encode(): String = NotionJson.encodeToString(JsonElement.serializer(), this)

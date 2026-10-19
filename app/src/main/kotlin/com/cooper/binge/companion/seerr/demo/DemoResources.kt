package com.cooper.binge.companion.seerr.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

private const val FIRST_ID = 100

/**
 * The server's settings pages as JSON the demo keeps: a page is an object a write merges its fields into, and a list
 * is objects with ids that can be added, edited and removed. The pages are typed where the app reads them, so a field
 * the app does not know is carried and ignored, and a field it sends lands where the next read finds it.
 */
internal class DemoResources {
    val objects = mutableMapOf<String, JsonObject>()
    val lists = mutableMapOf<String, MutableList<JsonObject>>()
    private var nextId = FIRST_ID

    fun nextId(): Int = nextId++
}

internal inline fun <reified T> toJson(value: T): JsonObject = demoJson.encodeToJsonElement(value) as JsonObject

internal fun parseObject(text: String): JsonObject = demoJson.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap())

internal fun jsonAnswer(element: JsonElement) = rawOk(element.toString())

private fun JsonObject.id(): Int? = get("id")?.jsonPrimitive?.int

/** `GET path` reads the page stored under [key], and a [write] to it merges the body's fields in and answers the result. */
internal fun DemoResources.objectRoutes(
    key: String,
    vararg path: String,
    write: String = "POST",
): List<DemoRoute> {
    val page = path.toList()
    return listOf(
        route("GET", page) { _, _ -> jsonAnswer(objects.getValue(key)) },
        route(write, page) { call, _ ->
            jsonAnswer(objects.getValue(key).let { JsonObject(it + parseObject(call.body)) }.also { objects[key] = it })
        },
    )
}

/** `GET path` lists, `POST path` adds one with the next id, and `PUT` and `DELETE path/{id}` edit and remove it. */
internal fun DemoResources.listRoutes(
    key: String,
    vararg path: String,
): List<DemoRoute> {
    val base = path.toList()
    val item = base + "{}"
    return listOf(
        route("GET", base) { _, _ -> jsonAnswer(JsonArray(lists.getValue(key))) },
        route("POST", base) { call, _ ->
            val added = JsonObject(parseObject(call.body) + ("id" to JsonPrimitive(nextId())))
            lists.getValue(key) += added
            jsonAnswer(added)
        },
        route("PUT", item) { call, (id) -> edit(key, id.toInt(), call.body) },
        route("DELETE", item) { _, (id) -> if (lists.getValue(key).removeAll { it.id() == id.toInt() }) done else demoNotFound },
    )
}

private fun DemoResources.edit(
    key: String,
    id: Int,
    body: String,
): DemoAnswer {
    val items = lists.getValue(key)
    val at = items.indexOfFirst { it.id() == id }
    if (at < 0) return demoNotFound
    items[at] = JsonObject(items[at] + parseObject(body))
    return jsonAnswer(items[at])
}

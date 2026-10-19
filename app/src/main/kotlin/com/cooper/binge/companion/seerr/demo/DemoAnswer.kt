package com.cooper.binge.companion.seerr.demo

import kotlinx.serialization.json.Json

private const val HTTP_OK = 200
private const val HTTP_NOT_IMPLEMENTED = 501

/** What the demo server says to one call: a status and a JSON body. */
internal class DemoAnswer(
    val code: Int,
    val body: String,
)

/** One call to the demo server: the method, the path under `/api/v1/`, one query parameter at a time, and the body. */
internal class DemoCall(
    val method: String,
    val path: String,
    private val query: (String) -> String?,
    private val readBody: () -> String,
) {
    val segments: List<String> = path.split('/').filter { it.isNotEmpty() }
    val body: String get() = readBody()

    fun query(name: String): String? = query.invoke(name)
}

internal val demoJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

internal inline fun <reified T> ok(value: T): DemoAnswer = DemoAnswer(HTTP_OK, demoJson.encodeToString(value))

/**
 * What every endpoint the demo does not serve answers. It is a server error on purpose: the screens already
 * have a way to show one, and the message says why, so a reviewer who reaches it is told rather than left to guess.
 */
internal val notInTheDemo = DemoAnswer(HTTP_NOT_IMPLEMENTED, """{"message":"This is not available in the demo."}""")

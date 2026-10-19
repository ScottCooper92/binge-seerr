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

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409

internal fun message(
    code: Int,
    text: String,
) = DemoAnswer(code, """{"message":"$text"}""")

internal val demoNotFound = message(HTTP_NOT_FOUND, "Not found.")
internal val demoBlocklisted = message(HTTP_FORBIDDEN, "This title is blocklisted.")
internal val demoAlreadyRequested = message(HTTP_CONFLICT, "Request for this media already exists.")

/** One call the demo serves: its method and path, where `{}` matches any one segment, and what answers it. */
internal class DemoRoute(
    val verb: String,
    val pattern: List<String>,
    val handle: (DemoCall, List<String>) -> DemoAnswer,
)

internal fun route(
    verb: String,
    vararg pattern: String,
    handle: (DemoCall, List<String>) -> DemoAnswer,
) = DemoRoute(verb, pattern.toList(), handle)

internal fun route(
    verb: String,
    pattern: List<String>,
    handle: (DemoCall, List<String>) -> DemoAnswer,
) = DemoRoute(verb, pattern, handle)

/** [body], already JSON, as a successful answer. */
internal fun rawOk(body: String) = DemoAnswer(HTTP_OK, body)

/** The first route that serves [call], in list order, so a literal path comes before a `{}` that would also match it. */
internal fun List<DemoRoute>.answer(call: DemoCall): DemoAnswer? =
    firstNotNullOfOrNull { route -> call.match(route.verb, route.pattern)?.let { route.handle(call, it) } }

/** The segments `{}` matched, in order, when [call] is `{verb} {pattern}`; null when it is not that call. */
internal fun DemoCall.match(
    verb: String,
    pattern: List<String>,
): List<String>? {
    if (method != verb || segments.size != pattern.size) return null
    val captured = mutableListOf<String>()
    pattern.forEachIndexed { index, part ->
        when (part) {
            "{}" -> captured += segments[index]
            segments[index] -> Unit
            else -> return null
        }
    }
    return captured
}

internal fun DemoCall.intQuery(
    name: String,
    default: Int,
) = query(name)?.toIntOrNull() ?: default

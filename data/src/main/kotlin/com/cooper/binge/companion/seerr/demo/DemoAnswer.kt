package com.cooper.binge.companion.seerr.demo

import com.cooper.homelab.core.server.demo.DemoAnswer
import com.cooper.homelab.core.server.demo.DemoCall
import com.cooper.homelab.core.server.demo.DemoRoute
import com.cooper.homelab.core.server.demo.demoNotAvailable
import com.cooper.homelab.core.server.demo.demoOk
import kotlinx.serialization.json.Json

internal val demoJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

internal inline fun <reified T> ok(value: T): DemoAnswer = demoOk(demoJson.encodeToString(value))

/**
 * What every endpoint the demo does not serve answers. It is a server error on purpose: the screens already
 * have a way to show one, and the message says why, so a reviewer who reaches it is told rather than left to guess.
 */
internal val notInTheDemo = demoNotAvailable("message")

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

/** Seerr's refusal to delete a title's files when none of its [client] servers is the default (the wording the app reads, #1726). */
internal fun demoNoDefaultServer(client: String) = message(HTTP_CONFLICT, "No $client server configured to delete media files")

/** A route whose pattern is already a list, as the generic resource routes build theirs. */
internal fun route(
    verb: String,
    pattern: List<String>,
    handle: (DemoCall, List<String>) -> DemoAnswer,
) = DemoRoute(verb, pattern, handle)

internal fun DemoCall.intQuery(
    name: String,
    default: Int,
) = query(name)?.toIntOrNull() ?: default

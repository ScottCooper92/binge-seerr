package com.cooper.binge.companion.seerr.telemetry

import android.util.Log
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.Logger

/** The shared library's [Logger], written to Logcat at the entry's own priority as `event name=value ...`. */
internal val LogcatLogger: Logger =
    Logger { level, tag, event, fields, error ->
        Log.println(level.priority(), tag, logcatLine(event, fields, error))
    }

private fun LogLevel.priority(): Int =
    when (this) {
        LogLevel.Info -> Log.INFO
        LogLevel.Warn -> Log.WARN
        LogLevel.Error -> Log.ERROR
    }

/**
 * One Logcat line: the constant [event], each field's kept value, and the throwable's class name. Never the throwable's
 * message, which can quote a body or an address.
 */
internal fun logcatLine(
    event: String,
    fields: List<LogField>,
    error: Throwable?,
): String =
    buildList {
        add(event)
        fields.forEach { add("${it.name}=${it.keptValue()}") }
        error?.let { add("error=${it::class.java.name}") }
    }.joinToString(" ")

private fun LogField.keptValue(): String =
    when (this) {
        is LogField.Count -> value.toString()
        is LogField.Elapsed -> value.toString()
        is LogField.Status -> code.toString()
        is LogField.Flag -> value.toString()
        is LogField.Case -> (value as? Enum<*>)?.name ?: value::class.java.simpleName
        is LogField.Server -> "saved"
        is LogField.Path ->
            url
                .substringAfter("://")
                .substringAfter('/', "")
                .substringBefore('?')
                .let { "/$it" }
        is LogField.Version -> value.toString()
        is LogField.Text -> value
    }

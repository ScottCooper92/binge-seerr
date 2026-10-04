package io.github.scottcooper92.binge.seerr.handoff

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val MAX_HEADER_BYTES = 8 * 1024
private const val MAX_BODY_BYTES = 4 * 1024

/**
 * The bounds on what the hand-off listener reads from one connection. Small on purpose: the only
 * request worth answering is a page fetch or a form carrying one address, so anything bigger is
 * refused before it is read.
 */
internal data class HandOffLimits(
    /** The request line and every header together, up to and including the blank line. */
    val maxHeaderBytes: Int = MAX_HEADER_BYTES,
    val maxHeaders: Int = 50,
    val maxBodyBytes: Int = MAX_BODY_BYTES,
    /** How long one read may block. */
    val readTimeoutMillis: Int = 5_000,
    /** How long the whole request may take to arrive, so a client cannot hold the one connection byte by byte. */
    val requestDeadlineMillis: Long = 10_000,
)

/** A request as far as the listener reads it: header names are lower-cased. */
internal data class HandOffRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

/** What reading a request came to: a request, or the status that refuses it. */
internal sealed interface ReadOutcome {
    data class Parsed(
        val request: HandOffRequest,
    ) : ReadOutcome

    data class Refused(
        val status: HttpStatus,
    ) : ReadOutcome
}

/** The statuses the listener answers with, and nothing else. */
internal enum class HttpStatus(
    val code: Int,
    val reason: String,
) {
    Ok(200, "OK"),
    BadRequest(400, "Bad Request"),
    NotFound(404, "Not Found"),
    PayloadTooLarge(413, "Payload Too Large"),
    HeadersTooLarge(431, "Request Header Fields Too Large"),
}

/** A response, always complete and always the last thing on its connection. */
internal data class HandOffResponse(
    val status: HttpStatus,
    val html: String,
) {
    /**
     * The headers keep the page to itself: no caching, no referrer (the token is in the URL), no
     * scripts or outside resources, and a form that can only post back here.
     */
    fun bytes(): ByteArray {
        val body = html.toByteArray(StandardCharsets.UTF_8)
        val head =
            buildString {
                append("HTTP/1.1 ${status.code} ${status.reason}\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Referrer-Policy: no-referrer\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append(
                    "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; " +
                        "form-action 'self'; base-uri 'none'; frame-ancestors 'none'\r\n",
                )
                append("Connection: close\r\n")
                append("\r\n")
            }
        return head.toByteArray(StandardCharsets.US_ASCII) + body
    }
}

/**
 * Reads one HTTP/1.1 request off [input] within [limits]. Not a general parser: no chunked bodies,
 * no continuation lines, no pipelining. Whatever falls outside that is refused rather than guessed at.
 */
internal class HandOffRequestReader(
    private val limits: HandOffLimits,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun read(input: InputStream): ReadOutcome =
        try {
            ReadOutcome.Parsed(parse(input))
        } catch (refusal: Refusal) {
            ReadOutcome.Refused(refusal.status)
        }

    private fun parse(input: InputStream): HandOffRequest {
        val deadline = clock() + limits.requestDeadlineMillis
        val lines = (readHead(input, deadline) ?: refuse(HttpStatus.HeadersTooLarge)).split("\r\n")
        val requestLine = lines.first().split(' ')
        if (requestLine.size != REQUEST_LINE_PARTS || !requestLine[2].startsWith("HTTP/1.")) refuse(HttpStatus.BadRequest)
        val headerLines = lines.drop(1).filter { it.isNotEmpty() }
        if (headerLines.size > limits.maxHeaders) refuse(HttpStatus.HeadersTooLarge)
        val headers =
            headerLines.associate { line ->
                val colon = line.indexOf(':').takeIf { it > 0 } ?: refuse(HttpStatus.BadRequest)
                line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            }
        if (headers.containsKey("transfer-encoding")) refuse(HttpStatus.BadRequest)
        val length = headers["content-length"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: refuse(HttpStatus.BadRequest) } ?: 0
        if (length > limits.maxBodyBytes) refuse(HttpStatus.PayloadTooLarge)
        val body = readBody(input, length, deadline) ?: refuse(HttpStatus.BadRequest)
        return HandOffRequest(requestLine[0], requestLine[1], headers, body)
    }

    /** How [parse] refuses: one exit for every reason, carrying the status to answer with. */
    private class Refusal(
        val status: HttpStatus,
    ) : Exception(status.reason)

    private fun refuse(status: HttpStatus): Nothing = throw Refusal(status)

    /** The head as ISO-8859-1 text without its blank line, or null when it outgrows the limit. */
    private fun readHead(
        input: InputStream,
        deadline: Long,
    ): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (matched < HEAD_END.size) {
            if (buffer.size() >= limits.maxHeaderBytes) return null
            checkDeadline(deadline)
            val next = input.read()
            if (next < 0) throw IOException("The connection closed mid-request")
            buffer.write(next)
            matched =
                if (next.toByte() == HEAD_END[matched]) {
                    matched + 1
                } else if (next.toByte() == HEAD_END[0]) {
                    1
                } else {
                    0
                }
        }
        val bytes = buffer.toByteArray()
        return String(bytes, 0, bytes.size - HEAD_END.size, StandardCharsets.ISO_8859_1)
    }

    private fun readBody(
        input: InputStream,
        length: Int,
        deadline: Long,
    ): ByteArray? {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            checkDeadline(deadline)
            val count = input.read(body, read, length - read)
            if (count < 0) return null
            read += count
        }
        return body
    }

    private fun checkDeadline(deadline: Long) {
        if (clock() > deadline) throw IOException("The request took too long to arrive")
    }

    private companion object {
        const val REQUEST_LINE_PARTS = 3
        val HEAD_END = "\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
    }
}

/** The fields of an `application/x-www-form-urlencoded` body; a malformed escape reads as no fields. */
internal fun ByteArray.formFields(): Map<String, String> =
    runCatching {
        String(this, StandardCharsets.UTF_8)
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val name = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                URLDecoder.decode(name, StandardCharsets.UTF_8.name()) to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }
    }.getOrDefault(emptyMap())

/** [this] safe to place in HTML text or a double-quoted attribute. */
internal fun String.escapeHtml(): String =
    buildString(length) {
        for (c in this@escapeHtml) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

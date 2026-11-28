package com.cooper.binge.companion.seerr.seerr

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Percent-encodes every character outside the unreserved set in a search value. The server checks the raw query string
 * with `express-openapi-validator`, which answers 400 for any of `: / ? # [ ] @ ! $ & ' ( ) * + , ; =` left unencoded, and OkHttp leaves `*` raw. So
 * `M*A*S*H` is refused and `M%2AA%2AS%2AH` is not. Only the free-text parameters are touched: `query` of the search
 * endpoints and `search` of the logs and the blocklist. Spaces stay `%20`, never `+`.
 */
internal class SeerrSearchQueryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val encoded = url.encodedQuery ?: return chain.proceed(request)
        val isSearchPath = url.encodedPath.contains("/api/v1/search")
        val rewritten =
            encoded.split('&').joinToString("&") { pair ->
                val name = pair.substringBefore('=')
                val isFreeText = (name == "query" && isSearchPath) || name == "search"
                if (!isFreeText || !pair.contains('=')) pair else "$name=${url.decodedValue(name, pair).encodeStrict()}"
            }
        if (rewritten == encoded) return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(url.newBuilder().encodedQuery(rewritten).build()).build())
    }

    private fun HttpUrl.decodedValue(
        name: String,
        pair: String,
    ): String {
        // The values of a repeated name come in order, so the one this pair holds is found by its raw form.
        val raw = pair.substringAfter('=')
        return HttpUrl
            .Builder()
            .scheme("http")
            .host("x")
            .encodedQuery("$name=$raw")
            .build()
            .queryParameter(name)
            .orEmpty()
    }

    private fun String.encodeStrict(): String {
        val out = StringBuilder()
        for (byte in toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and BYTE_MASK
            if (c.isUnreserved()) {
                out.append(c.toChar())
            } else {
                out.append('%').append(HEX[c shr NIBBLE_BITS]).append(HEX[c and NIBBLE_MASK])
            }
        }
        return out.toString()
    }

    private fun Int.isUnreserved(): Boolean = toChar() in UNRESERVED

    private companion object {
        val UNRESERVED: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + "-._~".toList()).toSet()
        const val HEX = "0123456789ABCDEF"
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0xF
        const val NIBBLE_BITS = 4
    }
}

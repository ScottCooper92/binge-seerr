package com.cooper.binge.companion.seerr.demo

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

private const val JSON = "application/json; charset=UTF-8"
private const val API_PREFIX = "/api/v1/"

/**
 * Answers every call to the demo server from [model], before the network: no socket is opened and nothing
 * leaves the device. A call to any other host passes through untouched, so this sits in every client and
 * is inert unless the saved server is the demo.
 *
 * It is the whole demo: the screens and the exported Service call the same [com.cooper.binge.companion.seerr.seerr.SeerrApi]
 * as against a real server, and read what this answers.
 */
internal class DemoTransport(
    val model: DemoModel = DemoModel(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isDemo()) return chain.proceed(request)
        val call =
            DemoCall(
                method = request.method,
                path = request.url.encodedPath.removePrefix(API_PREFIX),
                query = { request.url.queryParameter(it) },
                readBody = { request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }.orEmpty() },
            )
        val answer = model.answer(call)
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(answer.code)
            .message(if (answer.code < HTTP_ERROR_MIN) "OK" else "Not available in the demo")
            .body(answer.body.toResponseBody(JSON.toMediaType()))
            .build()
    }

    private companion object {
        const val HTTP_ERROR_MIN = 400
    }
}

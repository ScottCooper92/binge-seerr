package com.cooper.binge.companion.seerr.seerr

import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * True where the server behind the companion did not answer: the connection failed or timed out, or a 5xx says
 * nothing behind a proxy did. These carry `SERVER_UNREACHABLE` (#1913). A 4xx, an unreadable body and a missing
 * session are not here: the server answered, or there is no session to ask it with.
 */
fun Throwable.isServerUnreachable(): Boolean = this is IOException || (this is HttpException && isServerFailure())

internal fun HttpException.isServerFailure(): Boolean = code() >= HTTP_SERVER_ERROR_MIN

/** A connect, read or call timeout, which OkHttp reports as one of these two. */
internal fun IOException.isTimeout(): Boolean = this is SocketTimeoutException || this is InterruptedIOException

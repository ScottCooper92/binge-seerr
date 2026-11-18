package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrPageInfoDto

private const val DEFAULT_TAKE = 10

/** One page of [this] as the server pages: `take` and `skip` off the call, and the `pageInfo` that says how many there are. */
internal fun <T> List<T>.pageFor(call: DemoCall): Pair<List<T>, SeerrPageInfoDto> {
    val take = call.intQuery("take", DEFAULT_TAKE).coerceAtLeast(1)
    val skip = call.intQuery("skip", 0).coerceAtLeast(0)
    val pages = (size + take - 1) / take
    return drop(skip).take(take) to SeerrPageInfoDto(results = size, pages = pages, page = skip / take + 1)
}

package com.cooper.binge.companion.seerr.ui.hub

import com.cooper.binge.companion.seerr.seerr.SeerrQuotaBucketDto
import com.cooper.binge.companion.seerr.seerr.SeerrQuotaDto

fun SeerrQuotaDto.toHubQuota(): HubQuota = HubQuota(movie.toBucket(), tv.toBucket())

/** A bucket with no limit is unlimited, which is no bucket at all. */
private fun SeerrQuotaBucketDto?.toBucket(): HubQuotaBucket? {
    val limit = this?.limit?.takeIf { it > 0 } ?: return null
    return HubQuotaBucket(limit = limit, remaining = (remaining ?: (limit - (used ?: 0))).coerceIn(0, limit), days = days)
}

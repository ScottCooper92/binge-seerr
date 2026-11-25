package com.cooper.binge.companion.seerr.ui.hub

/** A per-type request quota; a null bucket is unlimited. */
data class HubQuota(
    val movie: HubQuotaBucket?,
    val tv: HubQuotaBucket?,
) {
    /** The metered bucket nearest its limit, the one the hub's ring speaks for. Null when neither is metered. */
    fun tightest(): TypedQuotaBucket? =
        listOfNotNull(
            movie?.let { TypedQuotaBucket(HubQuotaType.Movie, it) },
            tv?.let { TypedQuotaBucket(HubQuotaType.Tv, it) },
        ).maxByOrNull { it.bucket.usedFraction }
}

enum class HubQuotaType { Movie, Tv }

/** A quota bucket and which type of request it meters. */
data class TypedQuotaBucket(
    val type: HubQuotaType,
    val bucket: HubQuotaBucket,
)

data class HubQuotaBucket(
    val limit: Int,
    val remaining: Int,
    val days: Int?,
) {
    val used: Int get() = (limit - remaining).coerceIn(0, limit)

    /** [used] as a fraction of [limit]. A limit of none left to give reads as spent. */
    val usedFraction: Float get() = if (limit <= 0) 1f else used.toFloat() / limit
}

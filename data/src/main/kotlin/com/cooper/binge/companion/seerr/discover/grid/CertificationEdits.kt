package com.cooper.binge.companion.seerr.discover.grid

/**
 * A tap on a rating, as Binge's filter does it: it sets the most a title may be rated, keeping a minimum already set, a second tap on the
 * only one set clears it, and a tap on a lone maximum or minimum of the same rating makes it exact. Returns the new maximum and minimum.
 */
fun tapCertification(
    lte: String?,
    gte: String?,
    code: String,
): Pair<String?, String?> =
    when {
        lte == code && gte == null -> code to code
        lte == code && gte == code -> null to null
        else -> code to gte.takeUnless { it == lte }
    }

/** A long press on a rating: it sets the least a title may be rated, keeping a maximum already set, and cycles as [tapCertification] does. */
fun longPressCertification(
    lte: String?,
    gte: String?,
    code: String,
): Pair<String?, String?> =
    when {
        gte == code && lte == null -> code to code
        gte == code && lte == code -> null to null
        else -> lte.takeUnless { it == gte } to code
    }

/**
 * Whether a rating at [index] among [ratings] (in the server's order, youngest first) is inside the range set, or null while no range is
 * set. A rating outside the range is the one the filter leaves out.
 */
fun certificationIncluded(
    ratings: List<String>,
    index: Int,
    lte: String?,
    gte: String?,
): Boolean? {
    if (lte == null && gte == null) return null
    val low = ratings.indexOf(gte).takeIf { it >= 0 } ?: 0
    val high = ratings.indexOf(lte).takeIf { it >= 0 } ?: ratings.lastIndex
    return index in low..high
}

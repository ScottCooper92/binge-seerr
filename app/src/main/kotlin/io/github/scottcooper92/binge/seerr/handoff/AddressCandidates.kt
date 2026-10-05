package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.SeerrPublicSettings
import io.github.scottcooper92.binge.seerr.seerr.isValidBaseUrl
import io.github.scottcooper92.binge.seerr.seerr.normaliseBaseUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where an address the phone could send to a television came from. */
enum class AddressSource {
    /** Typed on this phone for this server before, and sent. */
    Remembered,

    /** The server's own Application URL, as its administrator set it. */
    ApplicationUrl,

    /** The address this phone is connected to the server on. */
    Connected,
}

/** One address the phone could send, already normalised, with where it came from and how local it is. */
data class AddressCandidate(
    val address: String,
    val source: AddressSource,
    val locality: AddressLocality,
)

/**
 * [raw] as one comparable server address: a scheme added where it was left off, scheme and host
 * lowercased, a default port dropped, user info, query and fragment removed, and a trailing slash.
 * Two spellings of one server come out equal. Null for anything that is not a server address, by the
 * same rule the television's own form applies ([isValidBaseUrl]).
 */
fun normaliseServerAddress(raw: String): String? {
    if (raw.isBlank() || !raw.isValidBaseUrl()) return null
    return raw
        .normaliseBaseUrl()
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.username("")
        ?.password("")
        ?.query(null)
        ?.fragment(null)
        ?.build()
        ?.toString()
}

/** The Application URL the administrator set, when there is one to offer: blank and absent are the same. */
fun SeerrPublicSettings.applicationUrlOrNull(): String? = applicationUrl?.takeIf { it.isNotBlank() }?.let(::normaliseServerAddress)

/**
 * The addresses to offer, best first, with no server named twice once normalised.
 *
 * [remembered] comes most recent first. The order is the locality first — [AddressLocality.Local], then
 * [AddressLocality.Unknown], then [AddressLocality.NotLocal], so an address the TV may not reach is
 * never the one already chosen while another exists — and within one locality an address the user
 * typed, then the server's Application URL, then the phone's own. The first is the one pre-selected.
 */
fun addressCandidates(
    connected: String,
    applicationUrl: String?,
    remembered: List<String>,
): List<AddressCandidate> {
    val raw =
        remembered.map { it to AddressSource.Remembered } +
            listOfNotNull(applicationUrl?.let { it to AddressSource.ApplicationUrl }) +
            (connected to AddressSource.Connected)
    return raw
        .mapNotNull { (address, source) -> normaliseServerAddress(address)?.let { it to source } }
        .distinctBy { it.first }
        .map { (address, source) -> AddressCandidate(address, source, addressLocality(address) ?: AddressLocality.Unknown) }
        .sortedWith(compareBy<AddressCandidate> { it.locality.preference }.thenBy { it.source.ordinal })
}

private val AddressLocality.preference: Int
    get() =
        when (this) {
            AddressLocality.Local -> 0
            AddressLocality.Unknown -> 1
            AddressLocality.NotLocal -> 2
        }

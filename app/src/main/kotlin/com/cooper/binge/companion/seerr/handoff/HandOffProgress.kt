package com.cooper.binge.companion.seerr.handoff

/**
 * Where the television is in connecting, as the phone's page shows it. The page re-reads this on every
 * request, so it follows the TV rather than being told once and left to go stale.
 */
sealed interface HandOffProgress {
    /** Nothing sent yet: the page offers the address form. */
    data object Waiting : HandOffProgress

    /** An address arrived and the TV is asking that server who it is. */
    data object Checking : HandOffProgress

    /**
     * An address a phone sent without a sealed session waits for the TV's user to go on before the TV reads that server
     * (#1084). A plain-HTTP address to a public host waits on the opt-in instead (#907). Either way the page says so
     * and follows the TV, which moves on to [Checking] once they do.
     */
    data object ConfirmOnTv : HandOffProgress

    /** The address sent was no Seerr server the TV could reach; the page offers the form again. */
    data object Failed : HandOffProgress

    /**
     * The address named a Seerr server, which has not finished its first-run setup, so there is nothing to sign in to yet (#1991).
     * The TV shows its own message and the page says the same, following the TV: it moves on once the server is set up and read
     * again, or goes back to the address when the TV's user changes server. No address is taken meanwhile, because the TV is
     * not on its address step.
     */
    data object NotSetUp : HandOffProgress

    /**
     * The server answered and the TV is on its sign-in step, named [server] and reached at [address], offering [modes]
     * by name. [failed] is whether the last attempt was refused, and [attempt] is how many sets of credentials the TV
     * has taken, so a phone can tell which attempt [failed] is about. Credentials are sealed for [address] (#1029): the
     * name is the server's own word for itself, which any server can claim.
     */
    data class SignIn(
        val server: String,
        val address: String,
        val modes: List<String> = emptyList(),
        val failed: Boolean = false,
        val attempt: Int = 0,
        val serverId: String? = null,
    ) : HandOffProgress

    /** Signed in: nothing left to do on the phone. */
    data object Connected : HandOffProgress

    /** Whether the page should offer the address form: nothing sent yet, or the last address failed. */
    val acceptsAddress: Boolean get() = this is Waiting || this is Failed
}

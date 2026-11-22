package com.cooper.binge.companion.seerr.ui

/** Everything the setup screen can ask of its ViewModel, in one place so the entry stays a wiring. */
class SetupActions(
    val onEditAddress: (String) -> Unit,
    val onInspect: () -> Unit,
    val onChangeServer: () -> Unit,
    val onEditForm: (SignInForm.() -> SignInForm) -> Unit,
    val onConnect: () -> Unit,
    val onPlexLaunched: () -> Unit,
    val onCancelLink: () -> Unit,
    val onRequestPasswordReset: () -> Unit,
    /** The opt-in to plain HTTP to a public host. Defaulted so a preview that never shows one need not wire it. */
    val onAllowCleartext: (Boolean) -> Unit = {},
    /** The television's hand-off from a phone (#323). Defaulted for the same reason: the phone never offers it. */
    val onStartHandOff: () -> Unit = {},
    val onCancelHandOff: () -> Unit = {},
    /** The TV's sign-in step asks for a code of its own when the address didn't come with one. */
    val onOfferSignInCode: () -> Unit = {},
    /** The local-network permission may have changed; read it again. Defaulted like [onAllowCleartext]. */
    val onLocalNetworkChanged: () -> Unit = {},
    /**
     * Leaving the saved server, offered beside the sign-in only when the app put the form up itself because the server
     * rejected the session (#810), so a user whose server is gone is not stuck on it. On TV it sits on the code page,
     * where that sign-in lands; the typed form's bar has no room for a third button. Null everywhere else.
     */
    val onDisconnect: (() -> Unit)? = null,
    /** Connects to the demo server (#1705). Defaulted: only the address step offers it. */
    val onTryDemo: () -> Unit = {},
    /** Reads a server again that said it was not set up. Defaulted: only that state offers it. */
    val onCheckAgain: () -> Unit = {},
)

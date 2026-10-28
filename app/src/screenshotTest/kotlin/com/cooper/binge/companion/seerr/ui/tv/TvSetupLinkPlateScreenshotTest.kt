package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.ui.LinkFlow
import com.cooper.binge.companion.seerr.ui.SignInForm

/**
 * The first television frames in this suite, and the reason the locale-pinned TV multipreview
 * exists: on the design system's own `@TvPreviews` these would record the en-XA pseudolocale.
 *
 * A sign-in that finishes elsewhere is all a remote can do with these two, so the code and the copy
 * telling the user where to take it are what the tab shows. Both flows are framed because they differ
 * only in that copy, which is exactly the part a frame can check and a focus test cannot.
 */
class TvSetupLinkPlateScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun quickConnect() =
        TvSetupScreen(
            state = setupSignIn(form = SignInForm(mode = SeerrSignInMode.QuickConnect), link = LinkFlow.QuickConnect(code = "472913")),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun plex() =
        TvSetupScreen(
            state =
                setupSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Plex),
                    link = LinkFlow.Plex(code = "JKLM", authUrl = "https://app.plex.tv/auth", launchPending = false),
                ),
            actions = NoSetupActions,
        )
}

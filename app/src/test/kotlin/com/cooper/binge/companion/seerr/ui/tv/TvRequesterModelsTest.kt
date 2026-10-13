package com.cooper.binge.companion.seerr.ui.tv

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.tv.requests.TvQuotaOverride
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequesterToggle
import com.cooper.binge.companion.seerr.ui.tv.requests.offersRequesterSheet
import com.cooper.binge.companion.seerr.ui.tv.requests.requesterState
import com.cooper.binge.companion.seerr.ui.tv.requests.withOverride
import com.cooper.binge.companion.seerr.ui.users.OWNER_USER_ID
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.GeneralSettings
import com.cooper.binge.companion.seerr.ui.users.settings.PermissionSettings
import com.cooper.binge.companion.seerr.ui.users.settings.QuotaDefault
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.requestItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val REQUESTER = 3

/** The requester sheet's gate and the state it puts together from the user's three editors (#1516). */
class TvRequesterModelsTest {
    @Test
    fun theSheetIsOfferedToAManagerForSomeoneElse() {
        assertTrue(offersRequesterSheet(requestDetail(canManageUsers = true)))
    }

    @Test
    fun theSheetIsNotOfferedWithoutManageUsers() {
        assertFalse(offersRequesterSheet(requestDetail(canManageUsers = false)))
    }

    @Test
    fun theSheetIsNeverOfferedForTheOwner() {
        assertFalse(offersRequesterSheet(requestDetail(item = requestItem(requestedById = OWNER_USER_ID), canManageUsers = true)))
    }

    @Test
    fun theSheetIsNotOfferedForTheViewersOwnRequestOrAnUnknownRequester() {
        assertFalse(offersRequesterSheet(requestDetail(viewerId = REQUESTER, canManageUsers = true)))
        assertFalse(offersRequesterSheet(requestDetail(item = requestItem(requestedById = null), canManageUsers = true)))
    }

    @Test
    fun theStateReadsThisMediaTypesQuotaAndOverride() {
        val state =
            requesterState(
                isMovie = false,
                user = ready(HubQuota(movie = HubQuotaBucket(5, 2, 7), tv = HubQuotaBucket(3, 1, 14))),
                permissions = permissions(PermissionSettings(selected = setOf(ManageablePermission.AutoApprove))),
                general = general(GeneralSettings(tvQuotaOverride = true, tvQuotaLimit = 3, tvQuotaDays = 14, canEditQuotas = true)),
            )

        assertEquals("ana", state.name)
        assertEquals(HubQuotaBucket(3, 1, 14), state.quota)
        assertTrue(state.quotaKnown)
        assertEquals(TvRequesterToggle(checked = true, enabled = true), state.autoApprove)
        assertEquals(TvRequesterToggle(checked = false, enabled = true), state.request4k)
        assertEquals(TvQuotaOverride(on = true, limit = 3, days = 14, default = null), state.override)
    }

    @Test
    fun aPermissionNotOfferedHasNoRowAndALockedOneIsDisabled() {
        val state =
            requesterState(
                isMovie = true,
                user = UserDetailUiState.Loading,
                permissions =
                    permissions(
                        PermissionSettings(
                            offered = listOf(ManageablePermission.AutoApprove),
                            locked = setOf(ManageablePermission.AutoApprove),
                        ),
                    ),
                general = ExtrasEditorUiState.Loading,
            )

        assertEquals(TvRequesterToggle(checked = false, enabled = false), state.autoApprove)
        assertNull(state.request4k)
        assertNull(state.override)
        assertNull(state.name)
        assertFalse(state.quotaKnown)
    }

    @Test
    fun theOverrideIsOnlyOfferedWhereTheServerLetsTheViewerEditQuotas() {
        val state =
            requesterState(
                isMovie = true,
                user = UserDetailUiState.Loading,
                permissions = EditorUiState.Loading,
                general = general(GeneralSettings(canEditQuotas = false)),
            )

        assertNull(state.override)
    }

    @Test
    fun aFailedSaveOutranksASaveInFlight() {
        val state =
            requesterState(
                isMovie = true,
                user = UserDetailUiState.Loading,
                permissions = permissions(PermissionSettings(), SaveStatus.Saving),
                general = general(GeneralSettings(), SaveStatus.Saved, failed = true),
            )

        assertEquals(SaveStatus.Failed, state.saveStatus)
    }

    @Test
    fun theWindowChoicesKeepAValueTheServerHoldsOffTheList() {
        assertEquals(listOf(1, 3, 7, 14, 30), TvQuotaOverride(true, 5, 7, null).windowChoices)
        assertEquals(listOf(1, 3, 7, 10, 14, 30), TvQuotaOverride(true, 5, 10, null).windowChoices)
    }

    @Test
    fun anOverrideIsWrittenToItsOwnMediaType() {
        val override = TvQuotaOverride(on = true, limit = 4, days = 30, default = QuotaDefault(10, 7))

        val movie = GeneralSettings().withOverride(isMovie = true, override)
        val tv = GeneralSettings().withOverride(isMovie = false, override)

        assertEquals(Triple(true, 4, 30), Triple(movie.movieQuotaOverride, movie.movieQuotaLimit, movie.movieQuotaDays))
        assertFalse(movie.tvQuotaOverride)
        assertEquals(Triple(true, 4, 30), Triple(tv.tvQuotaOverride, tv.tvQuotaLimit, tv.tvQuotaDays))
        assertFalse(tv.movieQuotaOverride)
    }

    private fun ready(quota: HubQuota?) =
        UserDetailUiState.Ready(
            UserDetail(
                item = UserItem(REQUESTER, "ana", null, null, null, UserOrigin.Local, 0, 0, null),
                permissions = emptySet(),
                quota = quota,
                watch = null,
                watchlist = emptyList(),
                isSelf = false,
                canEditSettings = true,
                canDelete = false,
                serverUrl = "https://seerr.example",
                webUrl = "https://seerr.example/users/$REQUESTER",
            ),
        )

    private fun permissions(
        settings: PermissionSettings,
        status: SaveStatus = SaveStatus.Idle,
    ) = EditorUiState.Ready(draft = settings, saved = settings, saveStatus = status)

    private fun general(
        settings: GeneralSettings,
        status: SaveStatus = SaveStatus.Idle,
        failed: Boolean = false,
    ) = ExtrasEditorUiState.Ready(draft = settings, saved = settings, extras = Unit, saveFailed = failed, saveStatus = status)
}

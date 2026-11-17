package com.cooper.binge.companion.seerr.ui.search

import com.cooper.binge.companion.seerr.ui.settings.server.NetworkForm
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.ServerUsersSettings
import com.cooper.homelab.core.uiandroid.search.SearchToggle

/**
 * One on/off setting the search can switch, over the record its page edits: how to read it and how to write it into the
 * draft. [read] answers null where the server does not have the setting, so its row has no switch. [allowed] says whether the
 * draft may be given the value asked for: the page refuses to save a draft that is not valid, so a flip that would make it so is
 * dimmed here, not shown and then dropped.
 */
internal class SearchSwitch<T>(
    val read: (T) -> Boolean?,
    val write: (T, Boolean) -> T,
    val allowed: (T, Boolean) -> Boolean = { _, _ -> true },
)

/** The switches on the server General page, by [SettingRows] key. Every other row there is text or a picker and keeps navigating. */
internal val generalSwitches: Map<String, SearchSwitch<ServerGeneralSettings>> =
    mapOf(
        SettingRows.GENERAL_HIDE_AVAILABLE to SearchSwitch({ it.hideAvailable }, { d, on -> d.copy(hideAvailable = on) }),
        SettingRows.GENERAL_HIDE_REQUESTED to SearchSwitch({ it.hideRequested }, { d, on -> d.copy(hideRequested = on) }),
        SettingRows.GENERAL_PARTIAL_REQUESTS to SearchSwitch({ it.partialRequests }, { d, on -> d.copy(partialRequests = on) }),
        SettingRows.GENERAL_SPECIAL_EPISODES to SearchSwitch({ it.specialEpisodes }, { d, on -> d.copy(specialEpisodes = on) }),
        SettingRows.GENERAL_CACHE_IMAGES to SearchSwitch({ it.cacheImages }, { d, on -> d.copy(cacheImages = on) }),
        SettingRows.GENERAL_TRUST_PROXY to SearchSwitch({ it.trustProxy }, { d, on -> d.copy(trustProxy = on) }),
        SettingRows.GENERAL_CSRF to SearchSwitch({ it.csrfProtection }, { d, on -> d.copy(csrfProtection = on) }),
    )

/** The switches on the Network page. */
internal val networkSwitches: Map<String, SearchSwitch<NetworkForm>> =
    mapOf(
        SettingRows.NETWORK_TRUST_PROXY to SearchSwitch({ it.trustProxy }, { d, on -> d.copy(trustProxy = on) }),
        SettingRows.NETWORK_CSRF to SearchSwitch({ it.csrfProtection }, { d, on -> d.copy(csrfProtection = on) }),
    )

/** The switch on the server Users page. */
internal val usersSwitches: Map<String, SearchSwitch<ServerUsersSettings>> =
    mapOf(
        SettingRows.USERS_LOCAL_LOGIN to
            SearchSwitch(
                read = { it.localLogin },
                write = { d, on -> d.copy(localLogin = on) },
                allowed = { d, on -> d.copy(localLogin = on).valid },
            ),
    )

/**
 * The toggle a result for [this] carries while its page's editor holds [draft]: the draft's value, a write through the page's own
 * [edit], a spinner while [saving], and no taps unless the viewer [mayChange] it and the flip is [SearchSwitch.allowed]. Null where the server lacks the setting.
 */
internal fun <T> SearchSwitch<T>.toggle(
    draft: T,
    saving: Boolean,
    mayChange: Boolean,
    edit: ((T) -> T) -> Unit,
): SearchToggle? {
    val on = read(draft) ?: return null
    return SearchToggle(
        checked = on,
        onCheckedChange = { wanted -> edit { write(it, wanted) } },
        enabled = mayChange && allowed(draft, !on),
        loading = saving,
    )
}

package com.cooper.binge.companion.seerr.ui.search

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.isJellyseerrLineage
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.settings.server.icon
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.titleRes

/**
 * The per-row entries of [settingsIndex]: each setting on a page, opening its page on the row. A row the server's reply decides
 * (Network's DNS cache, say) is not here.
 */
internal val settingRowEntries: List<IndexEntry> =
    buildList {
        add(setting(ServerSettingsPage.General, R.string.server_settings_application_title, Icons.Filled.Title, SettingRows.GENERAL_TITLE))
        add(
            setting(
                ServerSettingsPage.General,
                R.string.settings_application_url,
                Icons.Filled.Link,
                SettingRows.GENERAL_URL,
                R.string.search_kw_address,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.settings_display_language,
                Icons.Filled.Translate,
                SettingRows.GENERAL_LANGUAGE,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_api_key,
                Icons.Filled.VpnKey,
                SettingRows.GENERAL_API_KEY,
                R.string.search_kw_api_key,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_discover_region,
                Icons.Filled.Public,
                SettingRows.GENERAL_DISCOVER_REGION,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_streaming_region,
                Icons.Filled.LiveTv,
                SettingRows.GENERAL_STREAMING_REGION,
                shown = { it.profile.hasStreamingRegion },
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_discover_language,
                Icons.Filled.Language,
                SettingRows.GENERAL_DISCOVER_LANGUAGE,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.settings_hide_available,
                Icons.Filled.Visibility,
                SettingRows.GENERAL_HIDE_AVAILABLE,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_hide_requested,
                Icons.Filled.VisibilityOff,
                SettingRows.GENERAL_HIDE_REQUESTED,
                shown = { it.profile.variant.isJellyseerrLineage },
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_partial_requests,
                Icons.Filled.Layers,
                SettingRows.GENERAL_PARTIAL_REQUESTS,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_special_episodes,
                Icons.Filled.Event,
                SettingRows.GENERAL_SPECIAL_EPISODES,
                shown = { it.profile.variant.isJellyseerrLineage },
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_cache_images,
                Icons.Filled.Image,
                SettingRows.GENERAL_CACHE_IMAGES,
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_youtube_url,
                Icons.Filled.PlayCircle,
                SettingRows.GENERAL_YOUTUBE_URL,
                shown = { it.profile.variant.isJellyseerrLineage },
            ),
        )
        // Overseerr keeps these two on General; the forks moved them to Network.
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_trust_proxy,
                Icons.Filled.Shield,
                SettingRows.GENERAL_TRUST_PROXY,
                R.string.search_kw_proxy,
                shown = { it.profile.variant == SeerrVariant.Overseerr },
            ),
        )
        add(
            setting(
                ServerSettingsPage.General,
                R.string.server_settings_csrf,
                Icons.Filled.Lock,
                SettingRows.GENERAL_CSRF,
                shown = { it.profile.variant == SeerrVariant.Overseerr },
            ),
        )
        add(
            setting(
                ServerSettingsPage.Network,
                R.string.server_settings_trust_proxy,
                Icons.Filled.Shield,
                SettingRows.NETWORK_TRUST_PROXY,
                R.string.search_kw_proxy,
            ),
        )
        add(setting(ServerSettingsPage.Network, R.string.server_settings_csrf, Icons.Filled.Lock, SettingRows.NETWORK_CSRF))
        add(
            setting(
                ServerSettingsPage.Users,
                R.string.server_settings_local_login,
                Icons.AutoMirrored.Filled.Login,
                SettingRows.USERS_LOCAL_LOGIN,
                R.string.search_kw_sign_in,
            ),
        )
        add(
            setting(
                ServerSettingsPage.Users,
                R.string.server_settings_new_sign_ups,
                Icons.Filled.PersonAdd,
                SettingRows.USERS_NEW_SIGN_UPS,
                R.string.search_kw_sign_in,
            ),
        )
        add(setting(ServerSettingsPage.Users, R.string.server_settings_movie_limit, Icons.Filled.Movie, SettingRows.USERS_MOVIE_LIMIT))
        add(setting(ServerSettingsPage.Users, R.string.server_settings_tv_limit, Icons.Filled.Tv, SettingRows.USERS_TV_LIMIT))
        add(
            setting(
                ServerSettingsPage.Users,
                R.string.server_settings_default_permissions,
                Icons.Filled.Shield,
                SettingRows.USERS_DEFAULT_PERMISSIONS,
            ),
        )
        add(
            setting(
                ServerSettingsPage.Metadata,
                R.string.server_settings_metadata_tv,
                Icons.Filled.Tv,
                SettingRows.METADATA_TV,
                R.string.search_kw_metadata,
            ),
        )
        add(
            setting(
                ServerSettingsPage.Metadata,
                R.string.server_settings_metadata_anime,
                Icons.Filled.Animation,
                SettingRows.METADATA_ANIME,
                R.string.search_kw_metadata,
            ),
        )
        add(setting(UserSettingsPage.General, R.string.user_settings_display_name, Icons.Filled.Badge, SettingRows.ACCOUNT_DISPLAY_NAME))
        add(setting(UserSettingsPage.General, R.string.user_settings_email, Icons.Filled.Email, SettingRows.ACCOUNT_EMAIL))
        add(setting(UserSettingsPage.General, R.string.settings_display_language, Icons.Filled.Translate, SettingRows.ACCOUNT_LANGUAGE))
        add(
            setting(
                UserSettingsPage.General,
                R.string.server_settings_discover_region,
                Icons.Filled.Public,
                SettingRows.ACCOUNT_DISCOVER_REGION,
            ),
        )
        add(
            setting(
                UserSettingsPage.General,
                R.string.server_settings_discover_language,
                Icons.Filled.Language,
                SettingRows.ACCOUNT_DISCOVER_LANGUAGE,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_pgp,
                Icons.Filled.Key,
                SettingRows.NOTIFICATIONS_PGP_KEY,
                R.string.search_page_notifications_email,
                pageLabel = R.string.search_page_notifications_email,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_discord_id,
                Icons.Filled.Person,
                SettingRows.NOTIFICATIONS_DISCORD_ID,
                R.string.search_page_notifications_discord,
                pageLabel = R.string.search_page_notifications_discord,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_pushbullet_token,
                Icons.Filled.Key,
                SettingRows.NOTIFICATIONS_PUSHBULLET_TOKEN,
                R.string.search_page_notifications_pushbullet,
                pageLabel = R.string.search_page_notifications_pushbullet,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_pushover_token,
                Icons.Filled.Key,
                SettingRows.NOTIFICATIONS_PUSHOVER_APP_TOKEN,
                R.string.search_page_notifications_pushover,
                pageLabel = R.string.search_page_notifications_pushover,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_pushover_user,
                Icons.Filled.Person,
                SettingRows.NOTIFICATIONS_PUSHOVER_USER_KEY,
                R.string.search_page_notifications_pushover,
                pageLabel = R.string.search_page_notifications_pushover,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_pushover_sound,
                Icons.Filled.MusicNote,
                SettingRows.NOTIFICATIONS_PUSHOVER_SOUND,
                R.string.search_page_notifications_pushover,
                pageLabel = R.string.search_page_notifications_pushover,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_telegram_chat,
                Icons.Filled.Forum,
                SettingRows.NOTIFICATIONS_TELEGRAM_CHAT_ID,
                R.string.search_page_notifications_telegram,
                pageLabel = R.string.search_page_notifications_telegram,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_field_telegram_thread,
                Icons.Filled.Tag,
                SettingRows.NOTIFICATIONS_TELEGRAM_THREAD_ID,
                R.string.search_page_notifications_telegram,
                { it.profile.hasTelegramTopics },
                pageLabel = R.string.search_page_notifications_telegram,
            ),
        )
        add(
            setting(
                UserSettingsPage.Notifications,
                R.string.user_settings_telegram_silent,
                Icons.Filled.NotificationsOff,
                SettingRows.NOTIFICATIONS_TELEGRAM_SILENT,
                R.string.search_page_notifications_telegram,
                pageLabel = R.string.search_page_notifications_telegram,
            ),
        )
        add(
            appSetting(
                R.string.settings_report_bug,
                Icons.Filled.BugReport,
                SettingRows.APP_REPORT_BUG,
                R.string.search_kw_report_bug,
            ),
        )
        add(
            appSetting(
                R.string.settings_shake_to_report,
                Icons.Filled.Vibration,
                SettingRows.APP_SHAKE_TO_REPORT,
                R.string.search_kw_shake,
            ),
        )
        add(
            appSetting(
                R.string.settings_share_usage_data,
                Icons.Filled.Insights,
                SettingRows.APP_SHARE_USAGE_DATA,
                R.string.search_kw_usage_data,
            ),
        )
        add(
            appSetting(
                R.string.settings_send_crash_reports,
                Icons.Filled.Healing,
                SettingRows.APP_SEND_CRASH_REPORTS,
                R.string.search_kw_crash_reports,
            ),
        )
    }

private fun setting(
    page: ServerSettingsPage,
    @StringRes label: Int,
    icon: ImageVector,
    row: String,
    @StringRes keywords: Int? = null,
    @StringRes pageLabel: Int = page.label(),
    shown: (SearchAccess) -> Boolean = { true },
) = IndexEntry(SearchTarget.Server(page), label, icon, R.string.hub_section_settings, keywords, pageLabel, row, shown)

/** A setting on the This app page, which is not the server's: its own section, and the page's name in the results. */
private fun appSetting(
    @StringRes label: Int,
    icon: ImageVector,
    row: String,
    @StringRes keywords: Int,
) = IndexEntry(SearchTarget.ThisApp, label, icon, R.string.settings_group_app, keywords, R.string.hub_section_this_app, row)

private fun setting(
    page: UserSettingsPage,
    @StringRes label: Int,
    icon: ImageVector,
    row: String,
    @StringRes keywords: Int? = null,
    shown: (SearchAccess) -> Boolean = { true },
    @StringRes pageLabel: Int = page.titleRes(),
) = IndexEntry(SearchTarget.Account(page), label, icon, R.string.search_section_account, keywords, pageLabel, row, shown)

/** The page's title as its own entry shows it. */
@StringRes
private fun ServerSettingsPage.label(): Int =
    when (this) {
        ServerSettingsPage.General -> R.string.settings_group_general
        ServerSettingsPage.Users -> R.string.hub_section_users
        ServerSettingsPage.Network -> R.string.server_settings_network
        ServerSettingsPage.Metadata -> R.string.server_settings_metadata
        else -> error("$this has no setting entries yet")
    }

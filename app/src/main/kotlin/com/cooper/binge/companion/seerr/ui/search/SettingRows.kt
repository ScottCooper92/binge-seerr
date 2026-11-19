package com.cooper.binge.companion.seerr.ui.search

/**
 * The keys a settings page gives its rows (`ListItem.key`) so the settings search can open the page on one of them. A page and the
 * search both take the key from here, so the two cannot drift: a row is found by the name it is given. Each is "page.setting".
 */
internal object SettingRows {
    const val GENERAL_TITLE = "general.title"
    const val GENERAL_URL = "general.url"
    const val GENERAL_LANGUAGE = "general.language"
    const val GENERAL_API_KEY = "general.api-key"
    const val GENERAL_DISCOVER_REGION = "general.discover-region"
    const val GENERAL_STREAMING_REGION = "general.streaming-region"
    const val GENERAL_DISCOVER_LANGUAGE = "general.discover-language"
    const val GENERAL_HIDE_AVAILABLE = "general.hide-available"
    const val GENERAL_HIDE_REQUESTED = "general.hide-requested"
    const val GENERAL_PARTIAL_REQUESTS = "general.partial-requests"
    const val GENERAL_SPECIAL_EPISODES = "general.special-episodes"
    const val GENERAL_CACHE_IMAGES = "general.cache-images"
    const val GENERAL_YOUTUBE_URL = "general.youtube-url"
    const val GENERAL_TRUST_PROXY = "general.trust-proxy"
    const val GENERAL_CSRF = "general.csrf"

    const val NETWORK_TRUST_PROXY = "network.trust-proxy"
    const val NETWORK_CSRF = "network.csrf"

    const val USERS_LOCAL_LOGIN = "users.local-login"
    const val USERS_DEFAULT_PERMISSIONS = "users.default-permissions"

    const val METADATA_TV = "metadata.tv"
    const val METADATA_ANIME = "metadata.anime"

    const val ACCOUNT_DISPLAY_NAME = "account.display-name"
    const val ACCOUNT_EMAIL = "account.email"
    const val ACCOUNT_LANGUAGE = "account.language"
    const val ACCOUNT_DISCOVER_REGION = "account.discover-region"
    const val ACCOUNT_DISCOVER_LANGUAGE = "account.discover-language"

    const val APP_SHAKE_TO_REPORT = "app.shake-to-report"
    const val APP_SHARE_USAGE_DATA = "app.share-usage-data"
    const val APP_SEND_CRASH_REPORTS = "app.send-crash-reports"
}

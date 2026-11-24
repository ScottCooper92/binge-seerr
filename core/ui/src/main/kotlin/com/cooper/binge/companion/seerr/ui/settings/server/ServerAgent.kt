package com.cooper.binge.companion.seerr.ui.settings.server

/** The server's notification agents; [segment] is the path the server keys each on. */
enum class ServerAgent(
    val segment: String,
) {
    Email("email"),
    Discord("discord"),
    Telegram("telegram"),
    Pushover("pushover"),
    Pushbullet("pushbullet"),
    Slack("slack"),
    Gotify("gotify"),
    Ntfy("ntfy"),
    LunaSea("lunasea"),
    Webhook("webhook"),
    WebPush("webpush"),
}

# Server compatibility

This app connects to three servers that share one API: Overseerr, Jellyseerr and Seerr. They
are not the same server. Jellyseerr forked Overseerr, added Jellyfin and Emby, and kept adding
endpoints Overseerr never got. Seerr is Jellyseerr's next major version, with a rename that
reaches into the API (`/blacklist` became `/blocklist`). Overseerr stopped at 1.x.

So every feature in this app is gated on three things, and a screen reads the gate rather than
the raw facts behind it.

## The three gates

1. **The server's lineage and version.** What endpoints exist. Read once per connection from
   `GET /status`, kept in `SeerrServerProfile`.
2. **The signed-in user's permissions.** What the server would let this user do. Read from
   `GET /auth/me` as the permission bitmask, decoded by `SeerrPermissions`. `ADMIN` implies
   everything. Jellyseerr and Seerr define three bits Overseerr's current code does not:
   `MANAGE_SETTINGS`, `MANAGE_BLOCKLIST` and `VIEW_BLOCKLIST`. Overseerr up to 1.29 did define
   `MANAGE_SETTINGS` (bit 4), so an old grant of it can exist on any lineage.
3. **The server's configuration.** What the administrator turned on. Read from
   `GET /settings/public`: `localLogin`, `mediaServerLogin`, `mediaServerType`, `movie4kEnabled`,
   `series4kEnabled`, `partialRequestsEnabled`, `enableSpecialEpisodes`, `hideAvailable`,
   `emailEnabled` and the rest.

A feature is offered when all three say yes. The server's own `401`, `403` and `404` stay the
backstop, and a screen that meets one of them shows it rather than retrying.

## Telling the servers apart

`GET /status` returns `version`. The major is the lineage: `2.x` is Jellyseerr and `3.x` and above
is Seerr. `SeerrVariant.fromVersion` does that, and reads `1.x` as Overseerr by default.

A `1.x` is ambiguous, because Jellyseerr's first releases were 1.x too. So for a `1.x`, and for a
`develop-<sha>` build, which has no numeric version at all, the profile also reads
`GET /settings/public`: `mediaServerType` exists only on the Jellyseerr lineage, so its presence
picks the lineage. A `1.x` that has it is Jellyseerr at that version, with the features its
release has. A development build is taken to have everything its lineage has released.

When a call fails, the profile says so (`complete = false`) and is not cached, so the next read
tries again. A lineage that could not be read is taken as the family's latest, so a hiccup hides
nothing: it is never taken for Overseerr. The one exception is a `1.x` whose settings failed: it
reads as Overseerr, which hides Jellyseerr-only features rather than offering ones an Overseerr
would refuse, until the next read answers. A `mediaServerType` that was not read is
`SeerrMediaServer.Unknown`, not Plex, and the media-server page reports a failed load rather than
showing the Plex form for a Jellyfin server. Overseerr has no such field and is always Plex.

`commitTag`, `updateAvailable` and `commitsBehind` from the same call feed the update notice on
the hub. `versionCheck` in the public settings says whether the server checks at all.

## What each version has

The floor this app is tested against is **Overseerr 1.33**, **Jellyseerr 2.0** and **Seerr 3.0**.
Older servers still connect. Each feature is gated on the release its endpoint first shipped in,
so an older server shows less rather than failing on a call it cannot answer. The full list, per
endpoint, is in [`api-coverage.md`](api-coverage.md). The gates that change what a user sees:

| Feature | Overseerr | Jellyseerr / Seerr |
|---|---|---|
| Jellyfin and Emby sign-in, `POST /auth/jellyfin` | never | always |
| Plex sign-in, `POST /auth/plex` | always | when `mediaServerType` is Plex |
| Quick Connect sign-in | never | Seerr 3.4 |
| Blocklist | never | Jellyseerr 2.0 as `/blacklist`; Seerr 3.0 as `/blocklist` |
| Blocklist a whole collection | never | Seerr 3.2 |
| Automatic blocklist settings (Settings › General) | never | Jellyseerr 2.6 under `blacklist` names; Seerr 3.0 under `blocklist` names |
| Override rules (Settings › Services) | never | Jellyseerr 2.2 |
| Network settings (proxy, DNS) | never | Jellyseerr 2.4 |
| Linked Plex and Jellyfin accounts on a user | never | Jellyseerr 2.4 |
| ntfy notification agent | never | Jellyseerr 2.6 |
| Certifications filter on discover | never | Jellyseerr 2.6 |
| Metadata providers (Settings) | never | Seerr 3.0 |
| DNS cache flush | never | Seerr 3.0 |
| Delete media files from Radarr and Sonarr | never | Jellyseerr 1.5 |
| Media-server watchlist add and remove | never | Jellyseerr 1.6 |
| LunaSea notification agent | always | never |
| Discover sliders, watch providers, keyword and company search | 1.32 | Jellyseerr 1.4 |
| Combined RT and IMDb ratings | 1.34 | Jellyseerr 1.7 |
| Pushover sounds, read by any signed-in user (the route sits ahead of the admin-only `/settings` router) | 1.34 | Jellyseerr 1.8 |
| Telegram topic, `messageThreadId`, on a user and on the Telegram agent | never | Jellyseerr 2.2 |
| Issues, comments | 1.28 | always |
| Issue and request counts | 1.30 | always |

"Always" means the endpoint was in the first release of that lineage.

## Where the two lineages disagree on a body

An endpoint can exist on both lineages and still not take the same body. Some of these writes
**replace** rather than merge: the server assigns each field from what it read, so a key the body
leaves out is that setting cleared, not left alone. That is why a body sends fields this app does
not show, and why it sends both lineages' names for one value — each ignores the key it does not
know.

| Write | Overseerr | Jellyseerr / Seerr |
|---|---|---|
| `POST /user/{id}/settings/main` | `region`, `discordId` | `region` up to Jellyseerr 2.1; `discoverRegion` and `streamingRegion` from 2.2; `discordId` was dropped after Seerr 3.2 |
| `POST /user/{id}/settings/notifications` | `discordId` | `discordId` up to Seerr 3.2, the list `discordIds` from 3.3; `telegramMessageThreadId` throughout |
| `GET`/`PUT /settings/metadatas` | never | `{tv, anime}` at the top level, with no wrapper |

`PUT /request/{id}` replaces the whole destination on both, so a season-only edit still sends the
request's own `serverId`, `profileId`, `rootFolder` and `tags` back.

Three more are about where a value travels rather than what it is called:

- `POST /media/{id}/{status}` takes `is4k` on the **body**, on both lineages. As a query parameter
  it is a plain-instance write on Overseerr and a 500 on the Jellyseerr lineage, whose Express 5
  leaves `req.body` undefined for a bodyless call.
- `DELETE /blocklist/{tmdbId}` takes a `mediaType` query parameter, which the app sends only from
  Seerr 3.2. An earlier server rejects it as an unknown query parameter and answers 400. From 3.2
  the server needs it, and answers 400 without it.
- `GET /issue` accepts no reporter filter. The API spec declares only `requestedBy`, and the
  server's request validator answers 400 to any parameter the spec does not declare, `createdBy`
  included. The app sends neither: the server already narrows a user without `VIEW_ISSUES` or
  `MANAGE_ISSUES` to their own issues.

## What the request page shows of its destination

The request page reads the request's own `serverId`, `profileId`, `rootFolder` and `tags` from
`GET /request/{id}`, which both lineages serve, and names them from `GET /service/{radarr,sonarr}`
and `/service/{radarr,sonarr}/{id}`. Tags arrive as ids and are shown by label, so the labels are only
as available as that second lookup: where it fails (the administrator removed the instance, or the
server refuses this user) the tags are dropped rather than shown as numbers. A request made without
tags sends `"tags": null` on the Jellyseerr lineage, which reads as none. The tag row on the phone card and the lines on
the TV screen are read-outs, so they carry no permission gate of their own; the destination *editor*
still needs `REQUEST_ADVANCED`.

## The blocklist path

Jellyseerr 2.x serves the blocklist at `/blacklist`. Seerr 3.x serves it at `/blocklist` and
keeps `/blacklist` as an alias. The profile picks the path by version, and the REQUEST contract's
`CAPABILITY_BLOCK` is declared only when the lineage has a blocklist at all and the user holds
`MANAGE_BLOCKLIST`. Declaring it against an Overseerr server would offer Binge an action the
server answers with a `404`.

The General page's automatic blocklist settings were renamed the same way. Jellyseerr 2.6 sends
`hideBlacklisted`, `blacklistedTags` and `blacklistedTagsLimit`. Seerr 3.x sends `hideBlocklisted`,
`blocklistedTags` and `blocklistedTagsLimit`, and later added `blocklistRegion` and
`blocklistLanguage`. The page reads whichever names the server sent, shows only the settings that
are there, and saves under the same names. The server stores whatever a save sends, so writing the
other spelling would leave keys it never reads.

## Settings the administrator can turn off

Two public settings narrow the REQUEST contract's capabilities. The server's own code does not
enforce them all (for example, it never reads `partialRequestsEnabled` when it takes a request, and
it derives the 4K flags from the default 4K instances), so these gates mirror what the server's web
client offers:

- `CAPABILITY_REQUEST_4K` needs the user to hold the 4K permission for a media type the server has
  4K on for: `movie4kEnabled` for movies, `series4kEnabled` for series.
- `CAPABILITY_EDIT_SEASONS` needs `partialRequestsEnabled`. With it off the web client takes a whole
  show; the server itself does not check the setting. A user who may change the destination still
  gets the editor for that, with no season list. Its save sends the request's own `seasons` back,
  because both lineages answer a show's `PUT /request/{id}` without them with a 500.

## A capability this companion does not offer

`CAPABILITY_MEDIA_FILE_INFO`, a movie's file name, size, resolution and codec, is never declared,
and `RequestStatus.file_info` is never set. The contract makes both optional. Seerr's API does not
carry a finished download's file; only Radarr does. Radarr's host and API key are only in Seerr's
admin-only `GET /settings/radarr`. Serving it would mean holding a Radarr key and calling a second
host, for admins only. That would break the privacy promise that the app's server traffic goes only
to the Seerr server the user entered (and plex.tv for a Plex sign-in), never to a second host such
as Radarr (#483). If Seerr ever returns the file itself, this becomes a
mapping change.

## TLS and self-signed servers

A Seerr behind a self-signed certificate, or one from a private CA, works over HTTPS once that CA is
installed on the device (Settings → Security → Encryption & credentials → Install a certificate → CA
certificate). The app trusts user-installed CAs as well as the system's
(`res/xml/network_security_config.xml`), as Jellyfin's and Home Assistant's apps do. Without the CA
installed, the TLS handshake fails and the address step says the server could not be reached.

Plain HTTP is still allowed, because a LAN Seerr at a private IP is usually served that way and the
config file cannot express IP ranges. The app refuses an `http://` address that names a public host
until the user ticks a consent in setup, since the key would otherwise cross the internet in the
clear (`auth/CleartextConsent.kt`). Builds before that opt-in only warned.

## Where this lives

`SeerrServerProfile` is the one place a version is compared. It exposes named capabilities
(`hasBlocklist`, `blocklistPath`, `hasOverrideRules`, `hasQuickConnect`, `signInModes`, and so
on), and a screen or the Service asks for the capability. The same rule the contracts follow
applies here: a version answers "can we parse each other", and never "is this feature there".

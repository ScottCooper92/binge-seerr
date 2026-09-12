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
   everything. Jellyseerr and Seerr define three bits Overseerr does not: `MANAGE_SETTINGS`,
   `MANAGE_BLOCKLIST` and `VIEW_BLOCKLIST`.
3. **The server's configuration.** What the administrator turned on. Read from
   `GET /settings/public`: `localLogin`, `mediaServerLogin`, `mediaServerType`, `movie4kEnabled`,
   `series4kEnabled`, `partialRequestsEnabled`, `enableSpecialEpisodes`, `hideAvailable`,
   `emailEnabled` and the rest.

A feature is offered when all three say yes. The server's own `401`, `403` and `404` stay the
backstop, and a screen that meets one of them shows it rather than retrying.

## Telling the servers apart

`GET /status` returns `version`. The major is the lineage: `1.x` is Overseerr, `2.x` is
Jellyseerr, `3.x` and above is Seerr. That is what `SeerrVariant.fromVersion` does today.

A `develop-<sha>` build has no numeric version. For those the profile probes `GET /settings/public`:
`mediaServerType` exists only on the Jellyseerr lineage, so its presence picks the lineage, and a
development build is taken to have everything its lineage has released.

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
| Discover sliders, watch providers, keyword and company search | 1.32 | always |
| Combined RT and IMDb ratings | 1.34 | Jellyseerr 1.7 |
| Pushover sounds | 1.34 | Jellyseerr 1.8 |
| Issues, comments | 1.28 | always |
| Issue and request counts | 1.30 | always |

"Always" means the endpoint was in the first release of that lineage.

## The blocklist path

Jellyseerr 2.x serves the blocklist at `/blacklist`. Seerr 3.x serves it at `/blocklist` and
keeps `/blacklist` as an alias. The profile picks the path by version, and the REQUEST contract's
`CAPABILITY_BLOCK` is declared only when the lineage has a blocklist at all and the user holds
`MANAGE_BLOCKLIST`. Declaring it against an Overseerr server would offer Binge an action the
server answers with a `404`.

## Where this lives

`SeerrServerProfile` is the one place a version is compared. It exposes named capabilities
(`hasBlocklist`, `blocklistPath`, `hasOverrideRules`, `hasQuickConnect`, `signInModes`, and so
on), and a screen or the Service asks for the capability. The same rule the contracts follow
applies here: a version answers "can we parse each other", and never "is this feature there".

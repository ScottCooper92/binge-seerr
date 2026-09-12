# API coverage

Every endpoint the Seerr API surfaces, which phase of #26 covers it, and the first release of
each lineage that has it. The "Today" column marks the sixteen calls the app makes now.

The lists come from the OpenAPI specs (`overseerr-api.yml` on Overseerr's `develop`,
`seerr-api.yml` on Seerr's `develop`) and from the same file at every release tag of both
repositories, read on 2026-09-12. An endpoint's "since" is the first tag whose spec has it.
Jellyseerr 1.x, Jellyseerr 2.x and Seerr 3.x are one lineage with one tag history. "develop"
means the endpoint is on `develop` and in no release yet.

Two paths are the same endpoint spelled differently: Overseerr's `/tv/{tvId}/season/{seasonId}`
and Seerr's `/tv/{tvId}/season/{seasonNumber}`. The table lists it once.

How each gate is applied is in [`server-compatibility.md`](server-compatibility.md).

## Phases

- Phase 0 (#27), Phase 1 (#28), Phase 2 (#29), Phase 3 (#30), Phase 4 (#31), Phase 7 (#34),
  Phase 8 (#35). Phases 5, 6, 9, 10 and 11 add no endpoints: they are the poll, the host contract,
  multi-server, TV and release.
- "Not planned" is web push, which is the PWA's transport, and the first-run wizard, which stays
  the web client's.

## Endpoints

### Phase 0 — foundations

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `POST /auth/jellyfin` | Sign in using a Jellyfin username and password | — | v1.0.0 | yes | In place today. |
| `POST /auth/local` | Sign in using a local account | v1.17.0 | v1.0.0 | yes | In place today. |
| `GET /auth/me` | Get logged-in user | v1.0.0 | v1.0.0 | yes | Server profile: variant, version, media server, sign-in modes, permissions. |
| `GET /settings/public` | Get public settings | v1.0.0 | v1.0.0 |  | Server profile: variant, version, media server, sign-in modes, permissions. |
| `GET /status` | Get Seerr status | v1.15.0 | v1.0.0 | yes | Server profile: variant, version, media server, sign-in modes, permissions. |

### Phase 1 — hub, sign-in and settings

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `POST /auth/jellyfin/quickconnect/authenticate` | Authenticate with Quick Connect | — | v3.4.0 |  | Quick Connect sign-in, Seerr 3.4+. |
| `GET /auth/jellyfin/quickconnect/check` | Check Quick Connect authorization status | — | v3.4.0 |  | Quick Connect sign-in, Seerr 3.4+. |
| `POST /auth/jellyfin/quickconnect/initiate` | Initiate Jellyfin Quick Connect | — | v3.4.0 |  | Quick Connect sign-in, Seerr 3.4+. |
| `POST /auth/logout` | Sign out and clear session cookie | v1.20.0 | v1.0.0 |  | Disconnect ends the server session too, not only the local one. |
| `POST /auth/plex` | Sign in using a Plex token | v1.20.0 | v1.0.0 |  | Plex sign-in over the plex.tv PIN flow, then this call with the token. |
| `POST /auth/reset-password` | Send a reset password email | v1.20.0 | v1.0.0 |  | Forgot-password from the sign-in form (local accounts, email agent on). |
| `POST /auth/reset-password/{guid}` | Reset the password for a user | v1.20.0 | v1.0.0 |  | Forgot-password from the sign-in form (local accounts, email agent on). |
| `GET /backdrops` | Get backdrops of trending items | v1.27.0 | v1.1.0 |  | Artwork behind the sign-in form. |
| `GET /issue/count` | Gets issue counts | v1.30.0 | v1.1.1 |  | Hub counts; also the browsers' filter chips. |
| `GET /request/count` | Gets request counts | v1.17.0 | v1.0.0 |  | Hub counts; also the browsers' filter chips. |
| `GET /settings/about` | Get server stats | v1.5.0 | v1.0.0 |  | Versions and totals on the hub and in Settings. |
| `GET /settings/jobs` | Get scheduled jobs | v1.0.0 | v1.0.0 |  | Read-only Settings summary; the writes are Phase 8. |
| `GET /settings/main` | Get main settings | v1.0.0 | v1.0.0 |  | Read-only Settings summary; the writes are Phase 8. |
| `GET /settings/notifications/discord` | Get Discord notification settings | v1.0.0 | v1.0.0 |  | Agent status on the hub; editing is Phase 8. |
| `GET /settings/notifications/email` | Get email notification settings | v1.0.0 | v1.0.0 |  | Agent status on the hub; editing is Phase 8. |
| `GET /settings/radarr` | Get Radarr settings | v1.0.0 | v1.0.0 |  | Read-only Settings summary; the writes are Phase 8. |
| `GET /settings/sonarr` | Get Sonarr settings | v1.0.0 | v1.0.0 |  | Read-only Settings summary; the writes are Phase 8. |
| `GET /user/{userId}/quota` | Get quotas for a specific user | v1.22.0 | v1.0.0 |  | The signed-in user's quota on the hub; per user in Phase 4. |

### Phase 2 — requests

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `GET /request` | Get all requests | v1.0.0 | v1.0.0 |  |  |
| `POST /request` | Create new request | v1.0.0 | v1.0.0 | yes |  |
| `DELETE /request/{requestId}` | Delete request | v1.0.0 | v1.0.0 | yes |  |
| `GET /request/{requestId}` | Get MediaRequest | v1.0.0 | v1.0.0 |  |  |
| `PUT /request/{requestId}` | Update MediaRequest | v1.17.0 | v1.0.0 |  |  |
| `POST /request/{requestId}/retry` | Retry failed request | v1.14.0 | v1.0.0 | yes |  |
| `POST /request/{requestId}/{status}` | Update a request's status | v1.20.0 | v1.0.0 | yes |  |
| `GET /service/radarr` | Get non-sensitive Radarr server list | v1.17.0 | v1.0.0 | yes | Advanced picker (in place) and the edit-request modal. |
| `GET /service/radarr/{radarrId}` | Get Radarr server quality profiles and root folders | v1.17.0 | v1.0.0 | yes | Advanced picker (in place) and the edit-request modal. |
| `GET /service/sonarr` | Get non-sensitive Sonarr server list | v1.17.0 | v1.0.0 | yes | Advanced picker (in place) and the edit-request modal. |
| `GET /service/sonarr/lookup/{tmdbId}` | Get series from Sonarr | v1.18.0 | v1.0.0 |  | Advanced picker (in place) and the edit-request modal. |
| `GET /service/sonarr/{sonarrId}` | Get Sonarr server quality profiles and root folders | v1.17.0 | v1.0.0 | yes | Advanced picker (in place) and the edit-request modal. |

### Phase 3 — issues

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `GET /issue` | Get all issues | v1.28.0 | v1.1.0 |  |  |
| `POST /issue` | Create new issue | v1.28.0 | v1.1.0 | yes |  |
| `DELETE /issue/{issueId}` | Delete issue | v1.28.0 | v1.1.0 |  |  |
| `GET /issue/{issueId}` | Get issue | v1.28.0 | v1.1.0 |  |  |
| `POST /issue/{issueId}/comment` | Create a comment | v1.28.0 | v1.1.0 |  |  |
| `POST /issue/{issueId}/{status}` | Update an issue's status | v1.28.0 | v1.1.0 |  |  |
| `DELETE /issueComment/{commentId}` | Delete issue comment | v1.28.0 | v1.1.0 |  |  |
| `GET /issueComment/{commentId}` | Get issue comment | v1.28.0 | v1.1.0 |  |  |
| `PUT /issueComment/{commentId}` | Update issue comment | v1.28.0 | v1.1.0 |  |  |

### Phase 4 — users and blocklist

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `GET /blacklist` | Returns blocklisted items | — | v2.0.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `POST /blacklist` | Add media to blocklist | — | v2.0.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `DELETE /blacklist/{tmdbId}` | Remove media from blocklist | — | v2.0.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `GET /blacklist/{tmdbId}` | Get media from blocklist | — | v2.1.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `GET /blocklist` | Returns blocklisted items | — | v3.0.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `POST /blocklist` | Add media to blocklist | — | v3.0.0 | yes | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `DELETE /blocklist/collection/{collectionId}` | Remove collection from blocklist | — | v3.2.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `POST /blocklist/collection/{collectionId}` | Add collection to blocklist | — | v3.2.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `DELETE /blocklist/{tmdbId}` | Remove media from blocklist | — | v3.0.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `GET /blocklist/{tmdbId}` | Get media from blocklist | — | v3.0.0 |  | `/blacklist` on Jellyseerr 2.x, `/blocklist` from Seerr 3.0; never on Overseerr. |
| `GET /settings/jellyfin/users` | Get Jellyfin Users | — | v1.1.0 |  | Import users from the media server. |
| `GET /settings/plex/users` | Get Plex users | v1.29.0 | v1.1.0 |  | Import users from the media server. |
| `GET /user` | Get all users | v1.0.0 | v1.0.0 |  |  |
| `POST /user` | Create new user | v1.0.0 | v1.0.0 |  |  |
| `PUT /user` | Update batch of users | v1.18.0 | v1.0.0 |  |  |
| `POST /user/import-from-jellyfin` | Import all users from Jellyfin | — | v1.1.0 |  |  |
| `POST /user/import-from-plex` | Import all users from Plex | v1.12.0 | v1.0.0 |  |  |
| `GET /user/jellyfin/{jellyfinUserId}` | Get user by Jellyfin user ID | — | v3.3.0 |  |  |
| `DELETE /user/{userId}` | Delete user by ID | v1.0.0 | v1.0.0 |  |  |
| `GET /user/{userId}` | Get user by ID | v1.0.0 | v1.0.0 |  |  |
| `PUT /user/{userId}` | Update a user by user ID | v1.0.0 | v1.0.0 |  |  |
| `GET /user/{userId}/requests` | Get requests for a specific user | v1.20.0 | v1.0.0 |  |  |
| `DELETE /user/{userId}/settings/linked-accounts/jellyfin` | Remove the linked Jellyfin account for a user | — | v2.4.0 |  |  |
| `POST /user/{userId}/settings/linked-accounts/jellyfin` | Link the provided Jellyfin account to the current user | — | v2.4.0 |  |  |
| `POST /user/{userId}/settings/linked-accounts/jellyfin/quickconnect` | Link Jellyfin/Emby account with Quick Connect | — | v3.4.0 |  |  |
| `DELETE /user/{userId}/settings/linked-accounts/plex` | Remove the linked Plex account for a user | — | v2.4.0 |  |  |
| `POST /user/{userId}/settings/linked-accounts/plex` | Link the provided Plex account to the current user | — | v2.4.0 |  |  |
| `GET /user/{userId}/settings/main` | Get general settings for a user | v1.20.0 | v1.0.0 |  |  |
| `POST /user/{userId}/settings/main` | Update general settings for a user | v1.20.0 | v1.0.0 |  |  |
| `GET /user/{userId}/settings/notifications` | Get notification settings for a user | v1.20.0 | v1.0.0 |  |  |
| `POST /user/{userId}/settings/notifications` | Update notification settings for a user | v1.20.0 | v1.0.0 |  |  |
| `GET /user/{userId}/settings/password` | Get password page informatiom | v1.20.0 | v1.0.0 |  |  |
| `POST /user/{userId}/settings/password` | Update password for a user | v1.20.0 | v1.0.0 |  |  |
| `GET /user/{userId}/settings/permissions` | Get permission settings for a user | v1.20.0 | v1.0.0 |  |  |
| `POST /user/{userId}/settings/permissions` | Update permission settings for a user | v1.20.0 | v1.0.0 |  |  |
| `GET /user/{userId}/watch_data` | Get watch data | v1.29.0 | v1.1.0 |  |  |
| `GET /user/{userId}/watchlist` | Get the Plex watchlist for a specific user | v1.30.0 | v1.2.0 |  |  |

### Phase 7 — discover and title pages

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `GET /certifications/movie` | Get movie certifications | — | v2.6.0 |  |  |
| `GET /certifications/tv` | Get TV certifications | — | v2.6.0 |  |  |
| `GET /collection/{collectionId}` | Get collection details | v1.14.0 | v1.0.0 |  |  |
| `GET /discover/genreslider/movie` | Get genre slider data for movies | v1.22.0 | v1.0.0 |  |  |
| `GET /discover/genreslider/tv` | Get genre slider data for TV series | v1.22.0 | v1.0.0 |  |  |
| `GET /discover/keyword/{keywordId}/movies` | Get movies from keyword | v1.9.0 | v1.0.0 |  |  |
| `GET /discover/movies` | Discover movies | v1.0.0 | v1.0.0 |  |  |
| `GET /discover/movies/genre/{genreId}` | Discover movies by genre | v1.21.0 | v1.0.0 |  |  |
| `GET /discover/movies/language/{language}` | Discover movies by original language | v1.21.0 | v1.0.0 |  |  |
| `GET /discover/movies/studio/{studioId}` | Discover movies by studio | v1.21.0 | v1.0.0 |  |  |
| `GET /discover/movies/upcoming` | Upcoming movies | v1.0.0 | v1.0.0 |  |  |
| `GET /discover/trending` | Trending movies and TV | v1.0.0 | v1.0.0 |  |  |
| `GET /discover/tv` | Discover TV shows | v1.0.0 | v1.0.0 |  |  |
| `GET /discover/tv/genre/{genreId}` | Discover TV shows by genre | v1.21.0 | v1.0.0 |  |  |
| `GET /discover/tv/language/{language}` | Discover TV shows by original language | v1.21.0 | v1.0.0 |  |  |
| `GET /discover/tv/network/{networkId}` | Discover TV shows by network | v1.21.0 | v1.0.0 |  |  |
| `GET /discover/tv/upcoming` | Discover Upcoming TV shows | v1.20.0 | v1.0.0 |  |  |
| `GET /discover/watchlist` | Get the Plex watchlist. | v1.30.0 | v1.2.0 |  |  |
| `GET /genres/movie` | Get list of official TMDB movie genres | v1.21.0 | v1.0.0 |  |  |
| `GET /genres/tv` | Get list of official TMDB movie genres | v1.21.0 | v1.0.0 |  |  |
| `GET /keyword/{keywordId}` | Get keyword | v1.32.0 | v1.4.0 |  |  |
| `GET /languages` | Languages supported by TMDB | v1.20.0 | v1.0.0 |  |  |
| `GET /media` | Get media | v1.0.0 | v1.0.0 |  | Manage slideover on a title page: status, clear data, delete files, watch data. |
| `DELETE /media/{mediaId}` | Delete media item | v1.0.0 | v1.0.0 |  | Manage slideover on a title page: status, clear data, delete files, watch data. |
| `DELETE /media/{mediaId}/file` | Delete media file | — | v1.5.0 |  | Manage slideover on a title page: status, clear data, delete files, watch data. |
| `GET /media/{mediaId}/watch_data` | Get watch data | v1.29.0 | v1.1.0 |  | Manage slideover on a title page: status, clear data, delete files, watch data. |
| `POST /media/{mediaId}/{status}` | Update media status | v1.20.0 | v1.0.0 |  | Manage slideover on a title page: status, clear data, delete files, watch data. |
| `GET /movie/{movieId}` | Get movie details | v1.0.0 | v1.0.0 | yes |  |
| `GET /movie/{movieId}/ratings` | Get movie ratings | v1.0.0 | v1.0.0 |  |  |
| `GET /movie/{movieId}/ratingscombined` | Get RT and IMDB movie ratings combined | v1.34.0 | v1.7.0 |  |  |
| `GET /movie/{movieId}/recommendations` | Get recommended movies | v1.0.0 | v1.0.0 |  |  |
| `GET /movie/{movieId}/similar` | Get similar movies | v1.0.0 | v1.0.0 |  |  |
| `GET /network/{networkId}` | Get TV network details | v1.21.0 | v1.0.0 |  |  |
| `GET /person/{personId}` | Get person details | v1.0.0 | v1.0.0 |  |  |
| `GET /person/{personId}/combined_credits` | Get combined credits | v1.0.0 | v1.0.0 |  |  |
| `GET /regions` | Regions supported by TMDB | v1.20.0 | v1.0.0 |  |  |
| `GET /search` | Search for movies, TV shows, or people | v1.0.0 | v1.0.0 |  |  |
| `GET /search/company` | Search for companies | v1.32.0 | v1.4.0 |  |  |
| `GET /search/keyword` | Search for keywords | v1.32.0 | v1.4.0 |  |  |
| `GET /studio/{studioId}` | Get movie studio details | v1.21.0 | v1.0.0 |  |  |
| `GET /tv/{tvId}` | Get TV details | v1.0.0 | v1.0.0 | yes |  |
| `GET /tv/{tvId}/ratings` | Get TV ratings | v1.0.0 | v1.0.0 |  |  |
| `GET /tv/{tvId}/recommendations` | Get recommended TV series | v1.0.0 | v1.0.0 |  |  |
| `GET /tv/{tvId}/season/{seasonNumber}` | Get season details and episode list | v1.0.0 | v3.0.0 |  |  |
| `GET /tv/{tvId}/similar` | Get similar TV series | v1.0.0 | v1.0.0 |  |  |
| `POST /watchlist` | Add media to watchlist | — | v1.6.0 |  | Media-server watchlist from a title page. |
| `DELETE /watchlist/{tmdbId}` | Delete watchlist item | — | v1.6.0 |  | Media-server watchlist from a title page. |
| `GET /watchproviders/movies` | Get watch provider movies | v1.32.0 | v1.4.0 |  |  |
| `GET /watchproviders/regions` | Get watch provider regions | v1.32.0 | v1.4.0 |  |  |
| `GET /watchproviders/tv` | Get watch provider series | v1.32.0 | v1.4.0 |  |  |

### Phase 8 — server administration

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `GET /overrideRule` | Get override rules | — | v2.2.0 |  | Settings › Services › override rules, Jellyseerr 2.2+. |
| `POST /overrideRule` | Create override rule | — | v2.2.0 |  | Settings › Services › override rules, Jellyseerr 2.2+. |
| `DELETE /overrideRule/{ruleId}` | Delete override rule by ID | — | v2.2.0 |  | Settings › Services › override rules, Jellyseerr 2.2+. |
| `PUT /overrideRule/{ruleId}` | Update override rule | — | v2.2.0 |  | Settings › Services › override rules, Jellyseerr 2.2+. |
| `GET /settings/cache` | Get a list of active caches | v1.19.0 | v1.0.0 |  |  |
| `POST /settings/cache/dns/{dnsEntry}/flush` | Flush a specific DNS cache entry | — | v3.0.0 |  |  |
| `POST /settings/cache/{cacheId}/flush` | Flush a specific cache | v1.20.0 | v1.0.0 |  |  |
| `GET /settings/discover` | Get all discover sliders | v1.32.0 | v1.4.0 |  |  |
| `POST /settings/discover` | Batch update all sliders. | v1.32.0 | v1.4.0 |  |  |
| `POST /settings/discover/add` | Add a new slider | v1.32.0 | v1.4.0 |  |  |
| `GET /settings/discover/reset` | Reset all discover sliders | v1.32.0 | v1.4.0 |  |  |
| `DELETE /settings/discover/{sliderId}` | Delete slider by ID | v1.32.0 | v1.4.0 |  |  |
| `PUT /settings/discover/{sliderId}` | Update a single slider | v1.32.0 | v1.4.0 |  |  |
| `GET /settings/jellyfin` | Get Jellyfin settings | — | v1.0.0 |  |  |
| `POST /settings/jellyfin` | Update Jellyfin settings | — | v1.0.0 |  |  |
| `GET /settings/jellyfin/library` | Get Jellyfin libraries | — | v1.0.0 |  |  |
| `POST /settings/jellyfin/library/sync` | Sync Jellyfin libraries | — | develop |  |  |
| `PUT /settings/jellyfin/library/{libraryId}` | Update a single Jellyfin library | — | develop |  |  |
| `GET /settings/jellyfin/sync` | Get status of full Jellyfin library sync | — | v1.0.0 |  |  |
| `POST /settings/jellyfin/sync` | Start full Jellyfin library sync | — | v1.0.0 |  |  |
| `POST /settings/jobs/{jobId}/cancel` | Cancel a specific job | v1.20.0 | v1.0.0 |  |  |
| `POST /settings/jobs/{jobId}/run` | Invoke a specific job | v1.20.0 | v1.0.0 |  |  |
| `POST /settings/jobs/{jobId}/schedule` | Modify job schedule | v1.27.0 | v1.1.0 |  |  |
| `GET /settings/logs` | Returns logs | v1.22.0 | v1.0.0 |  |  |
| `POST /settings/main` | Update main settings | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/main/regenerate` | Get main settings with newly-generated API key | v1.20.0 | v1.0.0 |  |  |
| `GET /settings/metadatas` | Get Metadata settings | — | v3.0.0 |  |  |
| `PUT /settings/metadatas` | Update Metadata settings | — | v3.0.0 |  |  |
| `POST /settings/metadatas/test` | Test Provider configuration | — | v3.0.0 |  |  |
| `GET /settings/network` | Get network settings | — | v2.4.0 |  |  |
| `POST /settings/network` | Update network settings | — | v2.4.0 |  |  |
| `POST /settings/notifications/discord` | Update Discord notification settings | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/notifications/discord/test` | Test Discord settings | v1.12.0 | v1.0.0 |  |  |
| `POST /settings/notifications/email` | Update email notification settings | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/notifications/email/test` | Test email settings | v1.12.0 | v1.0.0 |  |  |
| `GET /settings/notifications/gotify` | Get Gotify notification settings | v1.29.0 | v1.1.0 |  |  |
| `POST /settings/notifications/gotify` | Update Gotify notification settings | v1.29.0 | v1.1.0 |  |  |
| `POST /settings/notifications/gotify/test` | Test Gotify settings | v1.29.0 | v1.1.0 |  |  |
| `GET /settings/notifications/lunasea` | Get LunaSea notification settings | v1.24.0 | v1.0.0 |  |  |
| `POST /settings/notifications/lunasea` | Update LunaSea notification settings | v1.24.0 | v1.0.0 |  |  |
| `POST /settings/notifications/lunasea/test` | Test LunaSea settings | v1.24.0 | v1.0.0 |  |  |
| `GET /settings/notifications/ntfy` | Get ntfy.sh notification settings | — | v2.6.0 |  |  |
| `POST /settings/notifications/ntfy` | Update ntfy.sh notification settings | — | v2.6.0 |  |  |
| `POST /settings/notifications/ntfy/test` | Test ntfy.sh settings | — | v2.6.0 |  |  |
| `GET /settings/notifications/pushbullet` | Get Pushbullet notification settings | v1.20.0 | v1.0.0 |  |  |
| `POST /settings/notifications/pushbullet` | Update Pushbullet notification settings | v1.20.0 | v1.0.0 |  |  |
| `POST /settings/notifications/pushbullet/test` | Test Pushbullet settings | v1.20.0 | v1.0.0 |  |  |
| `GET /settings/notifications/pushover` | Get Pushover notification settings | v1.16.0 | v1.0.0 |  |  |
| `POST /settings/notifications/pushover` | Update Pushover notification settings | v1.16.0 | v1.0.0 |  |  |
| `GET /settings/notifications/pushover/sounds` | Get Pushover sounds | v1.34.0 | v1.8.0 |  |  |
| `POST /settings/notifications/pushover/test` | Test Pushover settings | v1.16.0 | v1.0.0 |  |  |
| `GET /settings/notifications/slack` | Get Slack notification settings | v1.14.0 | v1.0.0 |  |  |
| `POST /settings/notifications/slack` | Update Slack notification settings | v1.14.0 | v1.0.0 |  |  |
| `POST /settings/notifications/slack/test` | Test Slack settings | v1.14.0 | v1.0.0 |  |  |
| `GET /settings/notifications/telegram` | Get Telegram notification settings | v1.15.0 | v1.0.0 |  |  |
| `POST /settings/notifications/telegram` | Update Telegram notification settings | v1.15.0 | v1.0.0 |  |  |
| `POST /settings/notifications/telegram/test` | Test Telegram settings | v1.15.0 | v1.0.0 |  |  |
| `GET /settings/notifications/webhook` | Get webhook notification settings | v1.17.0 | v1.0.0 |  |  |
| `POST /settings/notifications/webhook` | Update webhook notification settings | v1.17.0 | v1.0.0 |  |  |
| `POST /settings/notifications/webhook/test` | Test webhook settings | v1.17.0 | v1.0.0 |  |  |
| `GET /settings/notifications/webpush` | Get Web Push notification settings | v1.24.0 | v1.0.0 |  |  |
| `POST /settings/notifications/webpush` | Update Web Push notification settings | v1.24.0 | v1.0.0 |  |  |
| `POST /settings/notifications/webpush/test` | Test Web Push settings | v1.24.0 | v1.0.0 |  |  |
| `GET /settings/plex` | Get Plex settings | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/plex` | Update Plex settings | v1.0.0 | v1.0.0 |  |  |
| `GET /settings/plex/devices/servers` | Gets the user's available Plex servers | v1.18.0 | v1.0.0 |  |  |
| `GET /settings/plex/library` | Get Plex libraries | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/plex/library/sync` | Sync Plex libraries | — | develop |  |  |
| `PUT /settings/plex/library/{libraryId}` | Update a single Plex library | — | develop |  |  |
| `GET /settings/plex/sync` | Get status of full Plex library scan | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/plex/sync` | Start full Plex library scan | v1.20.0 | v1.0.0 |  |  |
| `POST /settings/radarr` | Create Radarr instance | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/radarr/test` | Test Radarr configuration | v1.0.0 | v1.0.0 |  |  |
| `DELETE /settings/radarr/{radarrId}` | Delete Radarr instance | v1.0.0 | v1.0.0 |  |  |
| `PUT /settings/radarr/{radarrId}` | Update Radarr instance | v1.0.0 | v1.0.0 |  |  |
| `GET /settings/radarr/{radarrId}/profiles` | Get available Radarr profiles | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/sonarr` | Create Sonarr instance | v1.0.0 | v1.0.0 |  |  |
| `POST /settings/sonarr/test` | Test Sonarr configuration | v1.0.0 | v1.0.0 |  |  |
| `DELETE /settings/sonarr/{sonarrId}` | Delete Sonarr instance | v1.0.0 | v1.0.0 |  |  |
| `PUT /settings/sonarr/{sonarrId}` | Update Sonarr instance | v1.0.0 | v1.0.0 |  |  |
| `GET /settings/tautulli` | Get Tautulli settings | v1.29.0 | v1.1.0 |  |  |
| `POST /settings/tautulli` | Update Tautulli settings | v1.29.0 | v1.1.0 |  |  |
| `GET /status/appdata` | Get application data volume status | v1.19.0 | v1.0.0 |  | Config-volume health, shown with About. |

### Not planned

| Endpoint | What it does | Overseerr since | Jellyseerr / Seerr since | Today | Note |
|---|---|---|---|---|---|
| `POST /settings/initialize` | Initialize application | v1.20.0 | v1.0.0 |  | The first-run wizard stays the web client's. |
| `POST /user/registerPushSubscription` | Register a web push /user/registerPushSubscription | v1.24.0 | v1.0.0 |  | Web push is the PWA's transport; this app polls (Phase 5). |
| `DELETE /user/{userId}/pushSubscription/{endpoint}` | Delete user push subscription by key | v1.35.0 | v2.7.0 |  | Web push is the PWA's transport; this app polls (Phase 5). |
| `GET /user/{userId}/pushSubscription/{endpoint}` | Get web push notification settings for a user | v1.35.0 | v2.7.0 |  | Web push is the PWA's transport; this app polls (Phase 5). |
| `GET /user/{userId}/pushSubscriptions` | Get all web push notification settings for a user | v1.34.0 | v2.5.2 |  | Web push is the PWA's transport; this app polls (Phase 5). |
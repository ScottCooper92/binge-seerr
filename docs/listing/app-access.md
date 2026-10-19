# App access, for Play review

The text for the Play Console's *App access* section, and a map of what a reviewer can reach. The app
manages a server the user already runs, and a reviewer has none, so the app has a demo: the same screens,
running on sample data built into the app. It needs no server, no account and no network beyond poster
images.

## What to paste into *App access*

> The app is an admin console for a Seerr, Jellyseerr or Overseerr server. No login is needed to review it.
> On the first screen, tap **Try the demo**. The app then runs every screen on sample data on the device,
> and a small "Demo server" tag on the home screen says so. Changes you make (approving a request, replying
> on an issue, blocking a title) stay until you tap **Exit the demo**, which clears them.
> To see the Binge integration, install Binge, allow this app in Binge's companion settings (it appears as
> "Seerr (demo)"), then look up or request a title from Binge.

## What to try

| Area | What the demo has |
|---|---|
| Home | Counts, a downloading strip, and the four sections. |
| Requests | Pending, downloading, failed, declined and available requests. Filter, sort, open one, **Review request**, approve or decline, remove, block. |
| Issues | One open issue with a short thread, one more open, one resolved. Comment, resolve, reopen, report a new one. |
| Users | Three users. Open one, change permissions and settings, add a user, import a sample account. |
| Blocklist | One blocked title. Block another from a request, unblock it. |
| Settings | The server's pages: General, Jellyfin, Services, Network, Metadata, Notifications, Logs, Jobs and cache, About, and the Discover sliders. Saving keeps the change. |
| Binge | The handshake, a request for any title, its status, and a report on an available title. |

On a television the same screens run with the remote, and **Try the demo** is at the bottom of the setup page.

## What the demo does not do

- It does not reach any server, so signing in with Plex or Jellyfin and linking a Plex or Jellyfin account
  are not in it, and a download server or notification agent "test" always passes. A screen that needs
  something the demo lacks says it is not available in the demo.
- Titles outside the sample set can still be requested from Binge. A well-known one keeps its name and
  poster from a short built-in list; any other is called "Requested title" and has no poster.
- Nothing is stored beyond the session: the sample data is rebuilt each time the demo starts.

## Keeping it true

The demo is the `demo` package. It has no data from any real server, and it never leaves the device: its
address is `demo.seerr.invalid`, which cannot resolve. Posters are TMDB image links, as with a real server.

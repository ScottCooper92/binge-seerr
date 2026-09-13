# binge-seerr

An Android admin console for a Seerr, Jellyseerr or Overseerr server, and the reference
companion app for [Binge](https://github.com/ScottCooper92/Binge).

## For the person running a server

Install it, enter your server's address, and sign in with an API key, a local account, Plex, or
Jellyfin's Quick Connect. The app then manages the server from your phone or your TV:

- **Requests**: every request on the server, filtered by state and sorted your way; approve,
  decline, retry or remove them, block a title from being requested again, and change a
  request's seasons, server, quality profile or root folder after the fact.
- **Issues**: what your users have reported, the thread under each, comment, resolve or reopen.
- **Users**: browse accounts, edit permissions in bulk, create local users or import them from the
  media server, and open a user's page for their requests, quota, watch history and settings.
- **Settings**: the server's general, media server, service, notification, discover, network,
  metadata, job, cache and log pages, editable where your account may edit them.
- **Notifications**: new requests waiting for approval, new issues, and your own requests being
  approved, declined or becoming available, checked in the background against your server. No
  third-party push service.
- **Android TV**: a rail with the hub, requests, issues and settings, driven from the remote.

What it shows depends on the server: Overseerr, Jellyseerr and Seerr share one API but not one
feature set, and your account's permissions decide what you may change. Each screen reads those
gates rather than guessing; [`docs/server-compatibility.md`](docs/server-compatibility.md) is the
list, and [`docs/api-coverage.md`](docs/api-coverage.md) every endpoint the app uses.

**With Binge installed**, this app also becomes Binge's request integration: a title page in Binge
gains a Request button, Binge shows each title's request state, and an advanced request opens here
with the server, profile and folder choices. Binge is optional; everything above works without it.

**It is not a discovery client.** Browsing and searching for titles is Binge's job, or the
server's own web client; this app manages what has already been asked for.

The store listing and the privacy policy are under [`docs/listing/`](docs/listing/). The short
version: the one secret (your API key or session) is encrypted with an Android Keystore key and
stays on the device; the app talks to the server you entered and, if you choose to sign in with
Plex, to plex.tv, and to nothing else.

## For the person writing a companion

Binge ships as a plain TMDB client with zero bundled providers. A companion app is a separate APK
that exports a bound Android Service implementing a contract from
[binge-integrations](https://github.com/ScottCooper92/binge-integrations); only data crosses the
IPC boundary, over gRPC on Binder, and there is no dynamic code loading. This app implements
REQUEST v1, and it is the **reference** companion: the worked example a third-party author reads
to learn what a companion looks like. Whatever is here will be copied, so the bar is not "does it
work" but "is this worth copying".

It has no privileged relationship with Binge. It talks through the same public contract and the
same handshake as anyone else's companion would; if something only worked because both ends share
an author, that would be a bug in the contract or a bug here, never a private arrangement.

The pieces to read, in order:

1. `service/SeerrCompanionService.kt`: the exported Service, built on the SDK's
   `IntegrationService`, and the caller policy that admits a debug host on debug builds and only
   Binge's published certificate on release builds.
2. `service/SeerrRequestService.kt`: every REQUEST v1 rpc, with the capability set derived from
   the signed-in user's permissions and the server's profile, and Seerr's failures mapped onto the
   gRPC status codes the contract documents.
3. `seerr/SeerrMediaIds.kt`: the translation from the contract's media identity (media type plus
   TMDB id, and season or episode) into the server's own id space. It is the single most
   interesting thing a companion demonstrates, so it lives in one place.
4. `AdvancedRequestActivity.kt`: the SDK's hand-off, the Activity Binge starts with a title so the
   companion can show its own request options.

The platform's decisions are in Binge's
[`docs/integration-platform-architecture.md`](https://github.com/ScottCooper92/Binge/blob/master/docs/integration-platform-architecture.md)
and the wire contract in binge-integrations' `contracts/`; this app's own roadmap is
[#26](https://github.com/ScottCooper92/binge-seerr/issues/26).

## Status

**Pre-alpha, at contract parity.** The exported Service serves every REQUEST v1 operation against
the connected server; the administration console covers requests, issues, users, the blocklist,
the server's settings pages and Android TV; release builds are shrunk, signed from a tag and
proven on a device lane. Not yet released: Binge's release signing certificate is not yet
published in the SDK, so a release build admits no host until it is, and the Play listing waits on
its screenshots.

## Building

The contracts and the SDK come from binge-integrations as source, and the theme and components
the screens wear from binge-design-system, both through a git submodule and a Gradle composite
build, so clone with submodules:

```sh
git clone --recurse-submodules https://github.com/ScottCooper92/binge-seerr
./gradlew build
```

That is the whole gate: Kotlin compile, unit tests, `ktlintCheck`, Android lint and the
translation-staleness check. JDK 17, AGP 9.3.2, `compileSdk` 37, `minSdk` 26, matching Binge,
so the extraction is a code move rather than a toolchain negotiation. The object graph is Hilt's,
through KSP, at the versions Binge pins; the wiring is one module, `di/SeerrModule.kt`.

A release build is shrunk by R8, with this app's own keep rules in `app/src/main/keepRules/`
on top of what the libraries ship. Measured on the unsigned release APK when it was turned on:
20.2 MB before, 3.4 MB after. What the gate above cannot see is whether those rules hold across a
real Binder, so `device-smoke.yml` runs one instrumentation test on a hosted emulator against the
minified build (`-PminifyDebug`, the same rules as release): it binds the exported Service and
completes a handshake and a status call. It runs on a push to `main` that touches what could
change its answer, weekly, and on request.

## Releasing

A release is a tag: `git tag v0.2.0 && git push origin v0.2.0`. `release.yml` runs the gate, builds
the release bundle signed with the upload key it decodes from the `RELEASE_KEYSTORE_BASE64`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` secrets, takes the
version name from the tag and the version code from the run number, checks the bundle is
release-signed and shrunk, and attaches it with the R8 mapping to a GitHub Release. A local signed
build reads the same four values from a gitignored `keystore.properties` at the root; without them
a release build signs with the debug key. The store listing, privacy policy and data-safety answers
live under `docs/listing/`, reviewed like code.

## Locales

`values/` is British English, declared as `en-GB` in `app/src/main/res/resources.properties`, and
Spanish ships beside it in `values-es/`. A locale is complete or it does not exist: the lint checks
for a missing or extra translation, a missing CLDR quantity and a placeholder that drifted are
pinned to error, so adding an English string means adding its Spanish in the same change. The one
drift lint cannot see, a reworded English string, is caught by `checkTranslationStaleness` (part
of `check`), which compares each translated source string against the hash committed in
`translation-hashes.txt`; re-read the translation it names, fix it if it no longer matches, then
re-stamp with `./gradlew updateTranslationHashes`. Debug builds also carry the `en-XA` and `ar-XB`
pseudolocales for truncation and mirroring checks before any translation is written.

## Conventions

`CLAUDE.md` holds the repository's conventions and is read from `main` by the agent
workflows in `.github/workflows/`. `.ai/agents/` holds the review checklist and the
CI triage table those workflows work from.

## Licence

Apache 2.0. See [LICENSE](LICENSE).

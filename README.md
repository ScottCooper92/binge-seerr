# binge-seerr

The reference companion app for [Binge](https://github.com/ScottCooper92).

Binge ships as a plain TMDB client with zero bundled providers. A companion app is a
separate APK that exports a bound Android Service implementing a contract from
[binge-integrations](https://github.com/ScottCooper92/binge-integrations). Only data
crosses the IPC boundary — there is no dynamic code loading. This app implements
REQUEST v1 against a [Seerr](https://overseerr.dev) instance.

## Status

**Pre-alpha, at contract parity.**

The app connects to a Seerr, Jellyseerr or Overseerr server (API key, or a Jellyfin,
Emby or local account) and serves every REQUEST v1 operation to Binge over gRPC on
Binder, built on the SDK from binge-integrations. The capability set Binge sees is
derived from the signed-in user's permissions. Not yet released: Binge's release
signing certificate is not yet published in the SDK, so a release build of this app
admits no host until it is.

## Why this repository is public

Two reasons, and the second is the one that shapes the code.

It is the **reference** companion — the worked example a third-party author reads to
learn what a companion looks like. Whatever is here will be copied, so the bar is not
"does it work" but "is this worth copying".

And it has no privileged relationship with Binge. It talks through the same public
contract and the same handshake as anyone else's companion. If something only worked
because both ends share an author, that would be a bug in the contract or a bug here,
and it needs to be one of those rather than a private arrangement.

## Building

The contracts and the SDK come from binge-integrations as source, and the theme and components
the screen wears from binge-design-system, both through a git submodule and a Gradle composite
build, so clone with submodules:

```sh
git clone --recurse-submodules https://github.com/ScottCooper92/binge-seerr
./gradlew build
```

That is the whole gate: Kotlin compile, unit tests, `ktlintCheck` and Android lint.
JDK 17, AGP 9.3.2, `compileSdk` 37, `minSdk` 26 — matching Binge, so the extraction
is a code move rather than a toolchain negotiation. The object graph is Hilt's, through
KSP, at the versions Binge pins; the wiring is one module, `di/SeerrModule.kt`.

A release build is shrunk by R8, with this app's own keep rules in `app/src/main/keepRules/`
on top of what the libraries ship. Measured on the unsigned release APK when it was turned on:
20.2 MB before, 3.4 MB after. What the gate above cannot see is whether those rules hold across a
real Binder, so `device-smoke.yml` runs one instrumentation test on a hosted emulator against the
minified build (`-PminifyDebug`, the same rules as release): it binds the exported Service and
completes a handshake and a status call. It runs on a push to `main` that touches what could
change its answer, weekly, and on request.

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

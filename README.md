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

The contracts and the SDK come from binge-integrations as source, through a git submodule
and a Gradle composite build, so clone with submodules:

```sh
git clone --recurse-submodules https://github.com/ScottCooper92/binge-seerr
./gradlew build
```

That is the whole gate: Kotlin compile, unit tests, `ktlintCheck` and Android lint.
JDK 17, AGP 9.3.2, `compileSdk` 37, `minSdk` 26 — matching Binge, so the extraction
is a code move rather than a toolchain negotiation.

## Conventions

`CLAUDE.md` holds the repository's conventions and is read from `main` by the agent
workflows in `.github/workflows/`. `.ai/agents/` holds the review checklist and the
CI triage table those workflows work from.

## Licence

Apache 2.0. See [LICENSE](LICENSE).

# CLAUDE.md

Conventions for this repository. The agent workflows in `.github/workflows/` read
this file from `main` and treat it as the source of truth; so should you.

## What this repository is

An Android admin console for a Seerr, Jellyseerr or Overseerr server, and the reference
companion app for [Binge](https://github.com/ScottCooper92). Those are its two jobs, in that
order for the person installing it and in the other order for the person reading it.

As a console, it manages the server the user already runs: requests, issues, users, the
blocklist, the server's settings pages and its notifications, on a phone and on Android TV. It
is not a discovery client; browsing for titles is Binge's job or the server's web client's.

As a companion, it is what Binge talks to. Binge ships as a plain TMDB client with zero
bundled providers; a companion app is a separate APK that exports a bound Android Service
implementing a contract from
[binge-integrations](https://github.com/ScottCooper92/binge-integrations). This one implements
REQUEST v1 against the connected server, and `AdvancedRequestActivity` is the SDK's hand-off,
the Activity Binge starts with a title.

The contracts live in that repository and are authoritative there. **Nothing in this
repository defines a contract.** A change to the wire format is a PR against
binge-integrations, and this repository consumes the result.

Status is pre-alpha, at contract parity: the exported Service serves every REQUEST v1
operation against the connected server, the capability set is derived from the signed-in
user's permissions and the server's profile, the console covers the server end to end, release
builds are shrunk, signed from a tag and proven on a device lane, and the app ships in English
and Spanish. Not yet released: Binge's release certificate is not yet published in the SDK, and
the Play listing under `docs/listing/` waits on its screenshots.

The contracts and the SDK are consumed as source: `binge-integrations/` is a git
submodule and `settings.gradle.kts` includes it as a composite build. So is the
shared design system, `design-system/` (binge-design-system): the screens are built
from its theme and components, which is what makes this app read as part of Binge
without depending on it. `.gitmodules` is an agent-governed path, so an author
bot's commit can revert a change to it — bump a submodule in a commit of its own,
and check both pins after any bot push.

## The one rule everything else serves

**This repository is read as documentation.**

It is public and it is called the reference companion. A third-party author writing
their own companion will read this code to learn what a companion looks like, and
will copy what they find. That makes "it works" insufficient on its own — the
question is whether the thing being copied is the thing you would want copied.

The practical consequence: no shortcut that depends on being first-party. This
companion has no privileged relationship with Binge, gets no special-cased
behaviour, and must work through the same public contract and the same handshake as
anyone else's. If something here only works because both ends share an author, it
is a bug in the contract or a bug in this app, and it needs to be one of those two
rather than a private arrangement.

## Talking to the host

- **Feature detection never uses version numbers.** A peer discovers what the other
  end can do from the handshake's capability set, and ignores enum values it does
  not know. Version numbers answer "can we parse each other?" and nothing else.
- **Errors are gRPC status codes**, never fields on a response message. Map Seerr's
  failures onto the codes the contract documents rather than inventing a shape.
- **Page every list, and send artwork as a URL rather than bytes.** The Binder
  transaction limit is about 1 MB and it is a hard ceiling, not a guideline. This is
  the constraint most easily forgotten on a small test library and most reliably hit
  on a real one.
- **Media is identified as media type + TMDB id (+ season/episode).** Translating
  that into Seerr's own id space is this app's job. That translation is the single
  most interesting thing this repository demonstrates, so it belongs in one clearly
  named place rather than spread across call sites.
- The exported Service is the attack surface. Whatever guards it — the handshake, a
  `SecurityPolicy` — is load-bearing and is not something to loosen to make a test
  pass.

## Talking to the server

Three servers share one API and not one feature set, and a user's account decides what they may
change, so **every feature is gated on three things, and a screen reads the gate rather than the
raw facts behind it** (`docs/server-compatibility.md` has the full list):

1. **Lineage and version**, from `GET /status` into `SeerrServerProfile`: which endpoints exist.
   Overseerr stopped at 1.x; Jellyseerr and Seerr are one lineage that kept adding.
2. **The signed-in user's permissions**, from `GET /auth/me` into `SeerrPermissions`: what the
   server would let this user do. `ADMIN` implies everything.
3. **The server's public settings**, from `GET /settings/public`: what the administrator turned on.

A feature that ignores a gate is a screen that shows a control the server will refuse, which is
the bug users report as "it does not work". This is a different rule from the host's
"capabilities, never versions": the host contract is versioned by capability because it is ours
to design; the server's API is versioned by release because it is not.

## Kotlin, Gradle and Android

- Kotlin via AGP's built-in support, `jvmTarget` 17, built and tested on JDK 17.
- AGP 9.3.2, `compileSdk` 37, `minSdk` 26, `targetSdk` 36 — matching Binge, so the
  extraction is a code move rather than a toolchain negotiation.
- The `org.jetbrains.kotlin.android` plugin is declared `apply false` in the root
  build and never applied. AGP 9 has built-in Kotlin support and rejects the plugin
  being applied, but compiles with whichever Kotlin Gradle plugin is on the build
  classpath, and the declaration is what puts 2.4.10 there — the version the included
  binge-integrations build compiles the contracts and SDK with. See the comment in
  `libs.versions.toml`.
- The version catalog is `libs.versions.toml` at the repository root, not under
  `gradle/`, matching Binge so a version bump applies the same way to both.
- Tests are JUnit 4, matching what the extracted code brings with it. Scope Gradle
  to a module (`:app:test`) rather than running the whole tree when you are checking
  one thing.

## Screens and ViewModels

The code was ported with these conventions and mostly keeps them. They are written down here
because this file is what the bots read, and an unwritten convention is one the next screen drifts
from without anyone noticing.

**State.** Every screen's state is a sealed interface: `Loading`, a data variant, and `Error` where
the screen can hard-fail. Never a flat class with `isLoading`-style flags. The shared generic form
is `ui/state/UiState.kt`, whose data variant is `Success`; a per-screen state names its data variant
`Ready`. Do not mix the two in one screen.

A flat data class is allowed for a screen whose top level is always interactive, with loading kept
elsewhere — and it carries a KDoc saying so. `LogsUiState` is the one: it holds only the query,
while loading and failure belong to the paging flow.

**Streams.** A ViewModel exposes **one** `StateFlow` for its state. Two other public flows are
allowed and no more: `Flow<PagingData<T>>`, which has to stay separate because `PagingData` is a
one-shot stream, and one-shot events. Anything else — a version counter, a "deleted" flag, a
refresh tick — is either state or an event, and belongs in one of the two.

Events use a per-screen sealed type, held as a private `MutableSharedFlow(extraBufferCapacity = 1)`
and exposed with `asSharedFlow()`. Editors inherit `EditorViewModel`'s `uiState` and `events`.
Never expose a `MutableStateFlow`. Collect with `collectAsStateWithLifecycle`.

**Sharing.** The policy follows what the screen is:

| Screen | Policy |
| --- | --- |
| Root: home, setup, TV home | `WhileSubscribed(5_000)` |
| List root | `Lazily`, with a `setScreenVisible` hook the entry calls from a `DisposableEffect` |
| Detail keyed on an id | `Lazily` |

A `Lazily` list root without the hook never refreshes on return. And a hook that re-reads
`connection.authenticatedUser()` or `connection.profile()` re-reads nothing: both are cached for the
life of the connection, so a scope that must see a server-side change asks for
`refreshAuthenticatedUser()` or `refreshProfile()` instead.

**Resources.** Every dp comes from `res/values/dimens.xml` through `dimensionResource`; no `.dp`
literals in production code. Every user-visible string comes from `strings.xml`, with its Spanish
in `values-es` — the Gates section covers what happens if it does not.

**Structure.** A screen composable orchestrates and delegates to focused children. About 300 lines
is the signal to split a file by concern, and 400 is too long. Route entries live in `*Entries.kt`
files by area. There is no automated length gate here — the custom detekt rule that enforces one in
Binge lives in an unpublished module (binge-integrations#39) — so this one is held in review.

**Where the code does not follow this**, it is an open issue rather than a line here: a list of
departures in this file goes stale faster than it is read. The one standing exception is the DVR
instance and override rule editors, which carry their pickers' choices in a second stream because
`EditorUiState<T>` has nowhere to put them; #188 is where that is being decided.

## Gates

CI runs `./gradlew build`. That is the whole gate, and it covers seven things, all of
which turn the build red:

- Kotlin compilation.
- The unit tests (`:app:test`).
- `ktlintCheck` — the ktlint plugin wires itself into `check`.
- `detekt` — static analysis of the Kotlin itself, which ktlint and Android lint do not do.
- **Android lint** (`:app:lint`) — AGP wires this into `check` too. It is easy to
  forget it is running until it fails, because nothing in the repository names it. The
  translation checks are pinned to error there: a locale is complete or it does not exist, so
  an English string added without its Spanish is a red build.
- `checkTranslationStaleness` — a reworded English string whose translation was not re-read.
  Fix or re-read the translation it names, then `./gradlew updateTranslationHashes`.
- `koverVerify` — line coverage below the floor.

`build` depends on `check`, which is what pulls the last six in. The device lane
(`device-smoke.yml`) is not part of it: it runs on `main`, weekly and on request, and proves the
release keep rules across a real Binder.

There is no screenshot suite and no `buf` in this repository, so do not look for one and
do not report a finding as though one had caught it.

### detekt

Config is the root `detekt.yml`, with `buildUponDefaultConfig = true`, so it records only
deviations from detekt's defaults. It is Binge's config minus everything that does not transfer:
the custom `binge:` ruleset lives in an unpublished module there and cannot be depended on from
here (binge-integrations#39), and the community Compose ruleset is tuned per-file to Binge's own
composables, so adopting it is a measurement pass rather than a port.

detekt runs on production `src/main` only, and **with type resolution** — the compile classpath is
wired onto the task in `app/build.gradle.kts`. Without that classpath `UnsafeCallOnNullableType`,
the `!!` ban, loads and silently never fires, so leave that wiring alone.

`detekt-baseline.xml` grandfathers what existed when the ruleset was switched on, so the gate is
**zero new violations** rather than zero total. Do not add to it: a new finding is either fixed or
argued with in review. Paying it down is #165.

### Coverage

`koverVerify` holds `:app` to **78% of lines**, a number measured (the suite was at 80.3%) rather
than inherited — Binge's 70 comes from its own per-module layout, and this is one module holding
ViewModels, the Seerr client, the stores and the exported service together.

What is excluded is generated code and Android entry points: Hilt's graph and the whole `di`
package, Room's `*_Impl*` including the inner classes it emits, `*ComposableSingletons*`,
`*PreviewData*`, the two Activities and the Application class, and every `@Composable` by
annotation — UI appearance is not a line-coverage question. Nothing is excluded for merely lacking
tests, and the patterns match fully-qualified names, so each needs its leading `*`.

The weak spots the figure is honest about are `ui/state` and `ui/tv`. Cover them and raise the
floor; do not lower it.

**Never silence a gate instead of fixing it.** Do not add a ktlint baseline, do not
add `ktlint` disable comments to make a file pass, do not add a `lint-baseline.xml`,
do not set `abortOnError = false`, do not exclude a source set from `check`, and do not
add to `detekt-baseline.xml`.
Formatting failures are the cheapest class of failure to fix properly. A lint
baseline is worse than it looks: it silences the finding permanently and silently,
in a repository whose whole purpose is to be copied.

## Follow-ups

Anything worth doing that does not belong in the diff in front of you becomes a
GitHub issue, not a TODO comment and not a line in a PR description.

The test is **scope, not severity**. A low-severity problem that reaches beyond the
current diff is still an issue; a serious problem inside the diff is a change to
make now. "Worth tracking", "worth confirming" and "shouldn't fall off the backlog"
all describe issues that should have been filed.

Dedupe against the open backlog before filing, and label it using the labels the
repository already has rather than inventing new ones.

## Commits and pull requests

- Conventional-commit subjects (`feat:`, `fix:`, `docs:`, `chore:`), imperative mood.
- One reviewable idea per PR. A PR that adds a contract call and refactors the build
  is two PRs.
- A PR that changes how this app talks to the host says in its body which contract
  version and which capabilities it relies on.
- Documentation in this repository is written in plain, direct English — short
  sentences, one idea each. Match the surrounding prose rather than introducing a
  different register.

## Agent workflows

`.github/workflows/` holds a review bot and three author bots, adapted from
binge-integrations for this repository. They act only on PRs carrying the `agent`
label.

**That label is maintainers-only.** Applying it grants an agent code execution with
this repository's secrets in scope. Do not apply it to a PR you have not read, and
never to one from a fork — the workflows already refuse fork PRs, and on a public
repository that guard is the load-bearing control rather than a formality.

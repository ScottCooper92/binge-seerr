---
name: audit
description: Whole-codebase (or package-scoped) sweep of binge-seerr for bugs, convention violations, doc-vs-code drift, dead code, server-gating gaps and weaknesses in the exported Service — the invariants CI does NOT gate, with the repo's exemption rules baked in so findings arrive pre-filtered, verified, and filed as issues. Use when asked to audit the codebase, hunt for bugs/drift/dead code, or health-check a package. For a diff, use /code-review or /security-review instead — this skill is not diff-scoped.
---

# Codebase audit

CI runs `./gradlew build`: compile, `:app:test`, ktlint, detekt (type-resolved, zero new
against `detekt-baseline.xml`), Android lint (translation checks and `UnusedResources` pinned to
error), `checkTranslationStaleness`, `koverVerify` (78%) and `validateDebugScreenshotTest`. Do not
re-report anything that set decides. The audit's value is what it *doesn't* gate: conventions held
"in review or nowhere" (`CLAUDE.md` > Screens and ViewModels), the one-rule-everything-serves
("this repository is read as documentation"), server-gating, the exported Service, and doc drift.

Scope to the whole repo or named packages under `app/src/main/kotlin/io/github/scottcooper92/binge/seerr/`
(`ui/`, `ui/tv/`, `seerr/`, `service/`, `auth/`, `data/`, `notifications/`, `feedback/`,
`telemetry/`, `di/`). Skip `binge-integrations/` and `design-system/` (submodules with their own
repos and audits) and `build/`.

**Three disciplines make this useful rather than noisy:**

1. **Exemptions first.** Check every hit against the exemption for that rule (below). Recurring
   ones: `src/test`, `src/androidTest`, `src/screenshotTest`, `*PreviewData*`, `@Preview` bodies,
   the sanctioned second stream on the DVR instance / override rule editors (#188), `LogsUiState`
   (flat state, documented), and generated/DI code under `di/`.
2. **Verify before reporting.** Re-read every candidate in context; label it **confirmed**
   (`file:line` and the exact violation or failure scenario) or **plausible** (needs a repro or a
   design decision). Never report a raw grep hit.
3. **Sanity-check the hunt.** Run each grep against a known positive before trusting a zero. Match
   by construction site, not bare property name (type-blind matches fake both directions). Manifest
   attributes, `res/xml`, `keepRules/`, Hilt/Room-generated code and `@Preview` functions are real
   references a grep-only sweep calls dead.

## Dimension 1 — ungated convention invariants (`CLAUDE.md` > Screens and ViewModels)

- **State shape**: a screen state that is a flat class with `isLoading`-style flags instead of a
  sealed interface (`Loading` / data / `Error`). Exempt `LogsUiState` (KDoc'd flat state).
- **One `StateFlow` per ViewModel**: count public `StateFlow`s per `*ViewModel.kt`; allowed extras
  are `Flow<PagingData<T>>` and one-shot events only. Any exposed `MutableStateFlow`. Exempt the
  DVR instance/override editors' picker stream (#188).
- **Events**: private `MutableSharedFlow(extraBufferCapacity = 1)` exposed via `asSharedFlow()`. A
  `tryEmit` on a flow with no buffer silently drops events; `replay = 1` for a one-shot re-fires on
  recomposition. Tests: `act(); vm.events.first()` is the race `awaitEvent` exists to avoid
  (`app/src/test/.../util/AwaitEvent.kt`).
- **Sharing policy** vs the table: root screens `WhileSubscribed(5_000)`; list roots `Lazily` **with**
  `setScreenVisible` called from a `DisposableEffect` (one half without the other never refreshes
  on return); detail screens keyed on an id `Lazily`. A `setScreenVisible` hook that re-reads
  `connection.authenticatedUser()` / `connection.profile()` re-reads nothing — those are cached for
  the connection's life; the scope must call `refreshAuthenticatedUser()` / `refreshProfile()`.
- **Resources**: inline dp (`grep -rnE '[0-9]+\.dp' --include='*.kt' app/src/main`; exempt `0.dp`
  floors, `@Preview`, `*PreviewData*`); hardcoded user copy (`Text("…")`, `stringResource`
  bypasses, display copy in Kotlin constants); every string with a `values-es` twin (lint enforces
  presence — check *quality/spelling* only if asked).
- **`collectAsState(` without `WithLifecycle`; `!!` in `src/main`** (detekt gates new ones; the
  baseline may hide old ones — read `detekt-baseline.xml`, which `CLAUDE.md` says must not grow).
- **Structure**: production `.kt` over 400 lines (no gate exists; `wc -l`), a screen composable
  over ~40 lines of UI that should have children, route entries outside `*Entries.kt`.
- **Never-silence rule**: any `@Suppress`, `ktlint-disable`, `abortOnError = false`,
  `lint-baseline.xml`, or growth in `detekt-baseline.xml` (it should only shrink, #165).

## Dimension 2 — the reference-companion rule and the host boundary

The repo is read as documentation; anything wrong here is copied by third parties.

- **Contract redefinition**: any `.proto`, hand-written message class or constant re-stating a
  contract value (the contract lives in binge-integrations). Anything relying on a private
  first-party arrangement (assumed field, undocumented ordering).
- **Capabilities, never versions**: a version-number comparison deciding host-facing behaviour; an
  unknown enum value treated as an error rather than ignored; an rpc reachable when its capability
  is undeclared.
- **Errors are gRPC status codes**: a Seerr failure mapped onto a response field, a generic
  `Status.UNKNOWN`/`INTERNAL` where the contract documents a code, or a catch that maps a real
  failure (auth, not-found, network) to the wrong code (`SeerrErrors.kt`, `service/`).
- **Paging and the 1 MB Binder ceiling**: every list rpc pages; artwork is a URL never bytes; page
  size bounded server-side of the boundary, not trusted from the caller.
- **Media identity translation in one place**: media type + TMDB id → Seerr id must live in one
  clearly named place (`SeerrMediaIds.kt`); flag translation spread across call sites.

## Dimension 3 — the exported Service and other attack surface

Read `AndroidManifest.xml`, `service/SeerrCompanionService.kt`, `SeerrRequestService.kt` and
the exported activities (`AdvancedRequestActivity`, the SETTINGS hand-off).

- The guard (handshake, `SecurityPolicy`) must not be loosened, optional, or bypassable by a
  code path (e.g. an rpc that skips the check, caller identity taken from a parameter).
- Exported Activities: every Intent extra treated as untrusted input (validate types, ranges,
  ids; no redirection of a received Intent, no `PendingIntent` mutability hazards; see the
  `android-intent-security` skill if available).
- `android:usesCleartextTraffic="true"` is set: check that plain HTTP is only reachable for a
  user-chosen server URL, never for Plex.tv / telemetry / update endpoints, and that credentials
  are not sent over cleartext without the user having been shown it.
- Secrets: API keys, session cookies, Plex tokens, server hostnames in logs, analytics, bug
  reports (`feedback/BugReport.kt`), crash keys or committed as literals (including tests); check
  `SecretCipher`/`CredentialStore` use (no plaintext fallback on decrypt failure).
- `allowBackup=false` — check `data_extraction_rules`/backup config does not re-expose stores.
- Blocking IO on a Binder or main thread; a bound Service's per-connection work or scope that
  outlives the connection; a held `Context`/`Activity`.

## Dimension 4 — server gating (`CLAUDE.md` > Talking to the server)

Every feature is gated on **lineage/version** (`SeerrServerProfile`), the **signed-in user's
permissions** (`SeerrPermissions`; `ADMIN` implies all) and the **server's public settings**;
a screen reads the *gate*, not the raw facts. Cross-check `docs/server-compatibility.md`:

- A screen/control that ignores a gate (shows what the server will refuse), or reads raw
  version/permission facts instead of the gate; a gate that **fails open** on error, timeout or a
  null profile/user.
- Endpoint used that `docs/api-coverage.md` does not list (or listed but unused); version cut-offs
  in code vs the doc; a Jellyseerr/Seerr-only endpoint called on Overseerr.
- A scope that must see a server-side change but uses the cached `authenticatedUser()`/`profile()`.

## Dimension 5 — correctness hunting (async, data, persistence)

- **Scope hygiene**: `GlobalScope`; `CoroutineScope(` outside `di/`; `runBlocking` in `src/main`;
  hardcoded `Dispatchers.*` (check how the repo injects them); scopes never cancelled.
- **Cancellation**: `runCatching`/`catch (e: Exception)` around suspend calls swallowing
  `CancellationException`; over-broad catches mapping distinct failures to one `UiError`;
  `Result.fold` branches dropping the error.
- **Partial loading gates**: `combine` inputs seeded with `onStart`, `emptyList()` standing in for
  "not loaded" (renders as absent, not pending); `MutableStateFlow` refresh/retry triggers that
  nothing increments (dead trigger with `Lazily` = fetched once per ViewModel).
- **Paging**: `cachedIn` missing/duplicated; `PagingData` re-emitted through a `StateFlow`;
  `RemoteMediator` (`IssuesRemoteMediator`, `UsersRemoteMediator`) end-of-pagination / refresh /
  cursor (`PageCursor`) errors; `LazyPagingItems` `items()` without `itemKey`.
- **Compose effects**: `LaunchedEffect(Unit)`/`DisposableEffect(Unit)` reading a changing
  parameter (fix pattern: `rememberUpdatedState`); state that must survive rotation held in
  `remember {}`; un-hoisted lazy-container state where scroll must survive.
- **Persistence**: Room schema vs exported schemas and migrations (`SeerrCacheDatabase`); renamed
  pref/store keys without migration; cache staleness (`TitleCache`, `MediaStatusFreshness`).
- **Notifications**: `NotificationWorker`/`PollReactor` duplicate notifications, channel ids
  renamed, work not cancelled on sign-out.
- **Weak spots the coverage figure admits**: `ui/state` and `ui/tv` — read these for logic that no
  test reaches.

## Dimension 6 — screenshot coverage gaps

`validateDebugScreenshotTest` guards only what has a frame. For each screen, enumerate the
sealed/enum cases its composables `when` over and diff against `app/src/screenshotTest` +
`*PreviewData*`: every unrendered state or new visual branch is zero-coverage. Also check: frames
pin the locale (unpinned frames render pseudolocale en-XA accented — see `SeerrPreviews.kt`); TV
screens have `@TvPreviews`-equivalent frames and focused states; previews rendering blank/empty
content; stateless `*Content` composables exist where a modal window cannot be captured.

## Dimension 7 — doc-vs-code drift

Extract every checkable claim from `CLAUDE.md`, `.ai/agents/*.md`, `docs/*.md`, `README.md` and
`.github/workflows/*.yml` comments (task names, thresholds — e.g. 78% kover, 400-line signal,
SDK/JDK/AGP versions, "pre-alpha at contract parity" status claims, capability lists, the
list of what `build` covers (compare it to `check`'s dependencies, not to a count), workflow names, paths, issue numbers like #165/#188) and
verify each against source, `libs.versions.toml`, `app/build.gradle.kts` and `buildSrc/`. Report
claim → reality → which side should change. Also check `docs/listing/` (data-safety, privacy)
against actual permissions, SDKs and data collected.

## Dimension 8 — dead, unreachable & orphaned code

Lint's `UnusedResources` is pinned to error and this is a single module, so unused
resources are already caught — verify the pin still holds (`app/build.gradle.kts`) rather than
re-hunting them. Hunt what lint cannot see:

- Unused `internal`/`private`/public non-`@Composable` declarations (exempt `@Preview`, Hilt
  `@Module`/`@Provides`/`@Binds` including multibindings, Room DAOs/entities, manifest-referenced
  and `keepRules/`-referenced classes, `@Serializable`/Moshi DTOs the deserializer builds).
  Known baseline entries: the two `UnusedPrivateMember` entries for `toCandidate()` in `UserAdmission.kt`
  are detekt false positives on extension receivers — the functions are used, so do not delete them.
- Dead enum entries / sealed subtypes; `SeerrApi` endpoints nothing calls; DTO fields never read.
- Never-mutated `MutableStateFlow`s (check all files); unreachable defaults (scope to construction
  sites of the type, split production from test).
- A constant or KDoc describing an abandoned mechanism.

## Output contract

- Rank by severity; **confirmed** before **plausible**; every finding grounded at `file:line` with
  the rule or the failure scenario (input → path → wrong result) stated.
- Substantive findings (real bug, gap, drift) → GitHub Issues, deduped against the open backlog
  first (`search_issues`; several "fix X" issues turn out already fixed), using labels the
  repository already has, grouped by theme (a scattering of dead symbols is one issue). Scope, not
  severity, decides issue vs fix-in-this-PR (`CLAUDE.md` > Follow-ups). Micro-nits → a short list
  in chat, not the backlog. Never a `// TODO` comment.
- State what was **not** audited. A silent partial audit reads as a clean bill; "checked, clean"
  per dimension is a valid result.

## Scaling

- **Quick** (default): greps + spot verification, one pass.
- **Thorough**: fan out parallel read-only subagents (one per dimension or package group), each
  told the exemptions above, then an adversarial verification pass (two skeptics per finding,
  default-refute) before anything is filed. This spends real tokens — do it only when the user
  opts in ("use a workflow").
- **Re-runs**: diff against the previous audit's issues — annotate or reopen rather than re-file.

# CLAUDE.md

Conventions for this repository. The agent workflows in `.github/workflows/` read
this file from `main` and treat it as the source of truth; so should you.

## What this repository is

The reference companion app for [Binge](https://github.com/ScottCooper92). Binge
ships as a plain TMDB client with zero bundled providers. A companion app is a
separate APK that exports a bound Android Service implementing a contract from
[binge-integrations](https://github.com/ScottCooper92/binge-integrations). This one
implements REQUEST v1 against a Seerr instance.

The contracts live in that repository and are authoritative there. **Nothing in this
repository defines a contract.** A change to the wire format is a PR against
binge-integrations, and this repository consumes the result.

Status is pre-alpha, at contract parity: the exported Service serves every REQUEST v1
operation against the connected server, the capability set is derived from the
signed-in user's permissions, and the UI is two screens — setup, and the advanced
request picker Binge hands a title to (`AdvancedRequestActivity`, the SDK's hand-off).
This replaced Binge's in-tree Seerr integration (roadmap stage 4 in binge-integrations).

The contracts and the SDK are consumed as source: `binge-integrations/` is a git
submodule and `settings.gradle.kts` includes it as a composite build. So is the
shared design system, `design-system/` (binge-design-system): the screen is built
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

## Gates

CI runs `./gradlew build`. That is the whole gate, and it covers four things, all of
which turn the build red:

- Kotlin compilation.
- The unit tests (`:app:test`).
- `ktlintCheck` — the ktlint plugin wires itself into `check`.
- **Android lint** (`:app:lint`) — AGP wires this into `check` too. It is easy to
  forget it is running until it fails, because nothing in the repository names it.

`build` depends on `check`, which is what pulls the last three in.

There is no detekt, no coverage floor, no screenshot suite and no `buf` in this
repository, so do not look for one and do not report a finding as though one had
caught it.

**Never silence a gate instead of fixing it.** Do not add a ktlint baseline, do not
add `ktlint` disable comments to make a file pass, do not add a `lint-baseline.xml`,
do not set `abortOnError = false`, and do not exclude a source set from `check`.
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

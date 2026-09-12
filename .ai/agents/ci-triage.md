# CI triage

Maps a red CI run to its correct fix. `author-ci-fix.yml` reads this file from
`main` and works from the table below; it gets exactly one repair attempt per
commit, so a guess is expensive and stopping is cheap.

CI is one job:

| Job | Runs |
| --- | --- |
| `build` | `./gradlew build` — Kotlin compile, `:app:test`, `ktlintCheck`, Android `lint` |

The last two arrive through `check`, which `build` depends on. Nothing in the
repository names them, so a red run whose log ends in a lint report is still the
`build` job, not a second gate that appeared from somewhere.

There is no detekt, no coverage floor, no screenshot suite and no `buf` here. If the
log shows a failure that is not in the table below, that is a **stop**, not an
invitation to improvise.

## The table

| Failure | Why | Fix |
| --- | --- | --- |
| `ktlintMainSourceSetCheck` / `ktlintTestSourceSetCheck` reports formatting | Style rule | Fix the formatting. `./gradlew ktlintFormat` fixes most of it mechanically; read the diff it produces rather than committing it blind. **Never** add a baseline or a disable comment |
| Android `lint` reports an error-severity issue | AGP's own checks, wired into `check` | Fix the code the issue describes. **Never** add `lint-baseline.xml`, `abortOnError = false`, or a `lintOptions` disable for the rule. A baseline in this repository is silently copied by everyone who reads it |
| Android `lint` reports `NewApi` | Code uses an API above `minSdk` 26 | Guard it with a version check or use the AndroidX-compatible call. Raising `minSdk` to make it pass is a **stop** — that is a product decision and it narrows who can install the companion |
| Kotlin compile error | Ordinary | Fix it. If a symbol from the contract stubs "does not exist", the app and the contract version it builds against disagree — fix the app, and if the contract is genuinely wrong that is a PR against binge-integrations, not a change here |
| `:app:test` — a unit test fails | Ordinary | Fix the code the test is describing. Changing an assertion to match new behaviour is only correct when the PR deliberately changed that behaviour and says so |
| `Failed to apply plugin 'org.jetbrains.kotlin.android'` | AGP 9 has built-in Kotlin support and rejects the plugin being applied to a module | Remove the `apply` from the module that applies it. The root build's own `apply false` declaration is intentional — it puts a compatible Kotlin version on AGP's classpath — and is not the plugin this error is about; do not remove that one. Do not downgrade AGP. See the comment in `libs.versions.toml` |
| Manifest merger failure | Two manifests declare conflicting attributes | Read the merger report it points at. Resolve by making the declarations agree; `tools:replace` is a last resort and needs saying why in the PR |
| Gradle "could not resolve" / dependency failure | Usually transient, or a catalog edit | If the diff touched `libs.versions.toml`, fix that. Otherwise it is infrastructure — **stop** and say so |
| Missing SDK component or unaccepted licence | Runner image lacks a `compileSdk`/build-tools version | **Stop.** Changing `compileSdk` to whatever the runner happens to have is not a repair; it silently changes what the app compiles against |
| AGP requires a newer Gradle, or vice versa | The diff bumped one of the pair | **Stop** unless the diff itself intended the bump. Bumping the other one to match is a real change that deserves its own PR |
| Empty or unreadable log | Nothing to triage from | **Stop.** The workflow already handles this and comments on the PR |

## Verifying

Run only what failed, scoped:

```sh
./gradlew :app:ktlintCheck
./gradlew :app:lintDebug
./gradlew :app:test
```

Do not run the full gate. CI does that on push.

## Never

- Add a ktlint baseline, a `lint-baseline.xml`, or a disable comment for either.
- Set `abortOnError = false` or otherwise stop lint from failing the build.
- Exclude a source set from `check`.
- Raise `minSdk`, or change `compileSdk`, to make a failure go away.
- Loosen whatever guards the exported Service to make a test pass.

## When in doubt

A red build is not permission to redesign. Leaving the working tree clean and saying
why is a valid and preferred outcome; a wrong guess costs more than stopping.

One thing specific to this repository: it is a **reference** companion, read by
people writing their own. A repair that makes CI green by demonstrating a bad
practice has made things worse even though the build passed.

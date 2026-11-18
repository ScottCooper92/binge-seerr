# Feature flags

Four flags. `BINGE_PROMOTION` decides what this app says about Binge. `REVIEW_PROMPT` decides whether it may ask for a Play review. `discovery_enabled` decides whether the discovery surfaces show (#2039). `search_toggles_enabled` decides whether a settings search result carries its own switch. The first three are off until Remote Config turns them on; the last has no remote value and is on only through a debug build's override.

| Flag | Remote Config key | Off means |
| --- | --- | --- |
| `BINGE_PROMOTION` | `binge_promotion_enabled` | Nothing about Binge: no prompt to install or open it, no hub tile or settings row, Binge-free wording in the setup intro and the disconnect confirmation. Titles open on the server's own page. |
| `REVIEW_PROMPT` | `review_prompt_enabled` | The app never asks the platform to show its review card. |
| `DiscoveryFlag` | `discovery_enabled` | The app is exactly as it was before the discovery epic: no bottom navigation, no Movies, TV or Search tabs, no title, person or collection pages. Nothing reads it yet. |
| `SearchTogglesFlag` | none: a debug override only | A settings search result for an on/off setting has no switch; it opens the setting, as before. On, the result carries the setting's own switch, which writes through the page's own editor. |

The `BINGE_PROMOTION` flag is about not pushing anyone to install an app they cannot connect to. It does not gate the integration
itself: the REQUEST and NOTIFICATIONS Service is always exported, and the hand-off Activities always answer an admitted
caller, so a Binge that finds this app can use it whatever the flag says.

`DiscoveryFlag` is this app's own, in `flags/DiscoveryFlag.kt`. It predates the shared library's open flag keys (an app declares
a `FlagKey` and lists it in the Firebase source), and moving it onto one is a follow-up. It reads the same Remote Config instance and keeps
the same one-hour fetch interval. A debug build can override it from the developer flags screen, in the same DataStore as the
other overrides under `override_discovery_enabled`; a release build has no override.

## How a value arrives

`FirebaseRemoteFlagSource` reads Firebase Remote Config. A build with no `google-services.json` has no Firebase
app, and the flag stays off. Remote Config keeps the last activated values on disk, so a cold start uses them
before the first fetch lands. The app fetches at start and each time `MainActivity` comes to the foreground; Remote
Config itself throttles that to once an hour.

### The first read is on the main thread

Hilt builds `RemoteFeatureFlags` while it injects `SeerrApp`, and `FlagsModule` passes `seedFromCache = true`, so
`source.current()` runs on the main thread in `Application.onCreate`. That call creates the Remote Config instance and reads the flag, which may wait for Remote
Config's cache to load from disk. It stays there on purpose: a value that arrived late would show a screen without Binge and then with it.

We measured it on 2026-10-26 and it is cheap enough to keep:

- Device: Pixel 10 Pro XL, Android 16 (`CP1A.260505.005`).
- Build: debug, at `cbc82895a`, with the debug app's own `google-services.json`, so Firebase was live.
- Method: `SystemClock.elapsedRealtimeNanos()` around `source.current()`, and a StrictMode thread policy with
  `detectDiskReads().penaltyLog()` set before `super.onCreate()`. One launch first, so the activated config was on
  disk. Then five cold starts: `am force-stop`, then `am start -W` of `MainActivity`. The instrumentation was not
  committed.

| | Median | Max |
| --- | --- | --- |
| `source.current()` on the main thread | 0.75 ms | 0.78 ms |
| of which creating the Remote Config instance | 0.59 ms | 0.62 ms |
| of which the two `getBoolean` reads (two flags at the time) | 0.11 ms | 0.12 ms |
| `am start -W` TotalTime | 860 ms | 885 ms |

StrictMode reported no disk read from Remote Config, `RemoteFlagSource` or `RemoteFeatureFlags` in any run. The very
first launch after install, with no cache yet, took 0.94 ms. The activated config held no keys at the time, so a
template with the flag adds only a few bytes to the file it reads. If the read ever costs more than about 5 ms,
move it to a background dispatcher and make the first frame of the Binge-facing surfaces wait for the first value, with a bound.

A debug build adds a **Feature flags** row to the hub's developer section. Tapping a flag cycles its local override:
Remote Config's value, on, off. A release build never reads overrides.

## Building from source

A build that was not given a `google-services.json` has no Remote Config, so the flag stays off and the app says
nothing about Binge. The integration still works: Binge finds and binds the Service as it would a release build. To
see the Binge prompts from a source build, point it at your own Firebase project and set the key to `true`, or use
the debug build's **Feature flags** row.

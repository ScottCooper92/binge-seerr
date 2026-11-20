# Feature flags

Five flags. `BINGE_PROMOTION` decides what this app says about Binge. `REVIEW_PROMPT` decides whether it may ask for a Play review. `discovery_enabled` decides whether the discovery surfaces show (#2039). `shared_setup_enabled` decides whether setup runs on the shared connect machine. `search_toggles_enabled` decides whether a settings search result carries its own switch. `BINGE_PROMOTION`, `REVIEW_PROMPT` and `shared_setup_enabled` are off until Remote Config turns them on. `discovery_enabled` is on until Remote Config turns it off. The last has no remote value and is on only through a debug build's override.

| Flag | Remote Config key | Off means |
| --- | --- | --- |
| `BINGE_PROMOTION` | `binge_promotion_enabled` | Nothing about Binge: no prompt to install or open it, no hub tile or settings row, Binge-free wording in the setup intro and the disconnect confirmation. Titles open on the server's own page. |
| `REVIEW_PROMPT` | `review_prompt_enabled` | The app never asks the platform to show its review card. |
| `DiscoveryEnabled` | `discovery_enabled` | The app is exactly as it was before the discovery epic: no bottom navigation, no Movies, TV or Search tabs, no title, person or collection pages. It defaults to on in the app, so a first run with nothing fetched, and a build with no Firebase app, show discovery; a fetched `false` is the kill switch. It is the remote half only: the app shows discovery when this flag is on and the user's own Discovery setting under This app is on too (default on), combined by core's shared `PreferenceGate`. |
| `SharedSetupEnabled` | `shared_setup_enabled` | Setup runs on the app's own `SetupViewModel`, as released builds always have. On, the entries take `SharedSetupViewModel` instead, which runs the address and sign-in steps on the shared library's connect machine. The screens are the same. It offers every sign-in the server does: the ones with fields (the key, the local account, Jellyfin and Emby), and Plex and Quick Connect, which run on the library's link flows. The pending link is kept encrypted in the saved state, so the wait is picked up again if the process dies while the user approves elsewhere. On the phone the setup draws the shared connect screen. The choice is made once when the setup entry first shows and is saved with it, so a value that arrives mid-setup, or a rotation after it, does not swap the view model. |
| `SearchToggles` | none: a debug override only | A settings search result for an on/off setting has no switch; it opens the setting, as before. On, the result carries the setting's own switch, which writes through the page's own editor. |

The `BINGE_PROMOTION` flag is about not pushing anyone to install an app they cannot connect to. It does not gate the integration
itself: the REQUEST and NOTIFICATIONS Service is always exported, and the hand-off Activities always answer an admitted
caller, so a Binge that finds this app can use it whatever the flag says.

`DiscoveryEnabled`, `SharedSetupEnabled` and `SearchToggles` are this app's own flags, declared as `FlagKey`s in `flags/FeatureFlags.kt` beside the
shared ones, and resolved by the same `FeatureFlags`. Nothing but the `PreferenceGate` built in `DiscoveryModule` reads `DiscoveryEnabled`: the gate combines it with the user's preference, and everything else reads the gate. The Firebase source reads
`RemoteFlagKeys`, which is the shared flags, `DiscoveryEnabled` and `SharedSetupEnabled`, from the one Remote Config instance with its one-hour fetch interval.
`SearchToggles` is deliberately not in that list: it has no remote value, so a Remote Config template cannot turn it on. A debug build
can override any of them from the developer flags screen, in the same DataStore as the other overrides under `override_discovery_enabled`,
`override_shared_setup_enabled` and `override_search_toggles_enabled`; a release build has no override.

## How a value arrives

`FirebaseRemoteFlagSource` reads Firebase Remote Config for `RemoteFlagKeys`. A build with no `google-services.json` has no Firebase
app, and each flag keeps its in-app default: off, except discovery, which is on. Remote Config keeps the last activated values on disk, so a cold start uses them
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

A build that was not given a `google-services.json` has no Remote Config, so the Binge flags stay off and the app says
nothing about Binge. Discovery is on, as its in-app default. The integration still works: Binge finds and binds the Service as it would a release build. To
see the Binge prompts from a source build, point it at your own Firebase project and set the key to `true`, or use
the debug build's **Feature flags** row.

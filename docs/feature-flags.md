# Feature flags

One flag decides what this app says about Binge. It is off until Remote Config turns it on.

| Flag | Remote Config key | Off means |
| --- | --- | --- |
| `BINGE_PROMOTION` | `binge_promotion_enabled` | Nothing about Binge: no prompt to install or open it, no hub tile or settings row, Binge-free wording in the setup intro and the disconnect confirmation. Titles open on the server's own page. |

The flag is about not pushing anyone to install an app they cannot connect to. It does not gate the integration
itself: the REQUEST and NOTIFICATIONS Service is always exported, and the hand-off Activities always answer an admitted
caller, so a Binge that finds this app can use it whatever the flag says.

## How a value arrives

`FirebaseRemoteFlagSource` reads Firebase Remote Config. A build with no `google-services.json` has no Firebase
app, and the flag stays off. Remote Config keeps the last activated values on disk, so a cold start uses them
before the first fetch lands. The app fetches at start and each time `MainActivity` comes to the foreground; Remote
Config itself throttles that to once an hour.

### The first read is off the main thread

`RemoteFeatureFlags` does no Remote Config work while Hilt builds it. Its first `refresh()` reads the activated values
on the IO dispatcher, so `Application.onCreate` never waits on Remote Config's cache.

The trade-off is the first moments after launch. Until that read lands, the flags hold the local overrides alone, so
the flag is off. A cold start whose activated config has `binge_promotion_enabled = true` composes its first frame
without Binge, and the Binge tile and wording appear once the read lands. A debug build avoids this for overrides by
reading them once at construction. Tracked in #2018, where the fix is a seed from homelab-core or a bounded wait on
the first frame.

A debug build adds a **Feature flags** row to the hub's developer section. Tapping a flag cycles its local override:
Remote Config's value, on, off. A release build never reads overrides.

## Building from source

A build that was not given a `google-services.json` has no Remote Config, so the flag stays off and the app says
nothing about Binge. The integration still works: Binge finds and binds the Service as it would a release build. To
see the Binge prompts from a source build, point it at your own Firebase project and set the key to `true`, or use
the debug build's **Feature flags** row.

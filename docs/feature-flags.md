# Feature flags

Two flags decide what this app says to, and does for, Binge. Both are off until Remote Config turns them on.

| Flag | Remote Config key | Off means |
| --- | --- | --- |
| `BINGE_PROMOTION` | `binge_promotion_enabled` | No prompt to install or open Binge. Titles open on the server's own page. |
| `REQUEST_COMPANIONS` | `request_companions_enabled` | The exported Service is disabled (the manifest ships it that way), so a host cannot find or bind it. A host that bound earlier gets an empty handshake and `PERMISSION_DENIED` for every REQUEST rpc. The hand-off Activities finish at once. Notification forwarding cannot be switched on and is never active. |

Anything about *connecting* to Binge needs both flags: the hub tile and settings row once Binge is installed,
the setup intro and the disconnect confirmation. `Flags.bingeConnectPromotion` holds that rule.

## The exported Service

The REQUEST contract's core (Handshake, SubmitRequest, GetStatus) cannot be served with the flag off, and a service
that cannot serve it must not export the REQUEST action. The manifest ships the Service component disabled, and `ExportedServiceGate` enables it with
the package manager only while the flag is on, and returns it to the manifest's state when the flag turns off. It applies the cached value as the
app starts and again after each fetch. The NOTIFICATIONS action lives on the same component and goes with it; forwarding
is never active while the flag is off, so nothing is lost.

A host that bound before the component was disabled is covered by the Service's own checks: an empty handshake,
`PERMISSION_DENIED` for every REQUEST rpc, and forwarding that cannot be switched on. `ListNotifications`,
`AcknowledgeNotifications` and turning forwarding off still answer such a host, so a queue left from before the flip can drain.

A host that already listed this app keeps the entry until it looks again. Turning the flag back on needs this app's
process to run, which the notification poll does every 15 minutes while it is scheduled, or the user opening the app.

## How a value arrives

`FirebaseRemoteFlagSource` reads Firebase Remote Config. A build with no `google-services.json` has no Firebase
app, and every flag stays off. Remote Config keeps the last activated values on disk, so a cold start uses them
before the first fetch lands. The app fetches at start and each time `MainActivity` comes to the foreground; Remote
Config itself throttles that to once an hour.

### The saved connection is decrypted off the main thread

The phone and TV home screens collect `CredentialStore.credentials` on the main thread, and its first value decrypts
the saved secret through the Android Keystore, which reads from disk. A cold start used to do that on the main
thread. `credentials` now decrypts on an injected IO dispatcher (`flowOn`), so the home screen stays on its spinner
until the value arrives. A lost Keystore key still reads as not connected.

We measured it on 2026-10-26, signed in to a Jellyseerr server:

- Device: Pixel 10 Pro XL, Android 16 (`CP1A.260505.005`).
- Build: debug, at `cbc82895a` plus this change, without a `google-services.json`.
- Method: a StrictMode thread policy with `detectDiskReads().penaltyLog()` set before `super.onCreate()`. Then five
  cold starts: `am force-stop`, then `am start -W` of `MainActivity`. The instrumentation was not committed.

| | Before (#1750's runs) | After |
| --- | --- | --- |
| `KeystoreSecretCipher.decrypt` main-thread disk reads | 1 per start, 3–9 ms | none in 5 starts |
| StrictMode disk-read violations of any kind | 1 per start | none in 5 starts |
| `am start -W` TotalTime, median | — | 807 ms |

As a control, the same instrumented build without the `flowOn` reported the decrypt violation on both of its two
cold starts. Each cold start showed the hub, connected.

A debug build adds a **Feature flags** row to the hub's developer section. Tapping a flag cycles its local override:
Remote Config's value, on, off. A release build never reads overrides.

## Building from source

A build that was not given a `google-services.json` has no Remote Config, so both flags stay off. The manifest ships
the Service disabled, so such a build has no Service for a host to find. To use the integration from a source build,
either point it at your own Firebase project and set the two keys to `true`, or use the debug build's **Feature flags**
row to switch them on locally. The Service is enabled once the app has run with the flag on.

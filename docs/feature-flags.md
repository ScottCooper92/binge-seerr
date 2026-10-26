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

A debug build adds a **Feature flags** row to the hub's developer section. Tapping a flag cycles its local override:
Remote Config's value, on, off. A release build never reads overrides.

## Building from source

A build that was not given a `google-services.json` has no Remote Config, so both flags stay off. The manifest ships
the Service disabled, so such a build has no Service for a host to find. To use the integration from a source build,
either point it at your own Firebase project and set the two keys to `true`, or use the debug build's **Feature flags**
row to switch them on locally. The Service is enabled once the app has run with the flag on.

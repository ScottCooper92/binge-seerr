# Feature flags

Two flags decide what this app says to, and does for, Binge. Both are off until Remote Config turns them on.

| Flag | Remote Config key | Off means |
| --- | --- | --- |
| `BINGE_PROMOTION` | `binge_promotion_enabled` | No prompt to install or open Binge. Titles open on the server's own page. |
| `REQUEST_COMPANIONS` | `request_companions_enabled` | The Service answers the handshake with no capabilities and serves no rpc. The hand-off Activities finish at once. Notification forwarding cannot be switched on and is never active. |

Anything about *connecting* to Binge needs both flags: the hub tile and settings row once Binge is installed,
the setup intro and the disconnect confirmation. `Flags.bingeConnectPromotion` holds that rule.

## How a value arrives

`FirebaseRemoteFlagSource` reads Firebase Remote Config. A build with no `google-services.json` has no Firebase
app, and every flag stays off. Remote Config keeps the last activated values on disk, so a cold start uses them
before the first fetch lands. The app fetches at start and each time `MainActivity` comes to the foreground; Remote
Config itself throttles that to once an hour.

A debug build adds a **Feature flags** row to the hub's developer section. Tapping a flag cycles its local override:
Remote Config's value, on, off. A release build never reads overrides.

## Off for the Service

The Service stays bindable. Binge already handles a companion that declares nothing, so refusing the bind would only
turn a quiet state into an error. The handshake is not recorded, so "Connected to Binge" never advances.

## Building from source

A build that was not given a `google-services.json` has no Remote Config, so both flags stay off and the Service
declares nothing. That is inside the contract: an OK handshake with an empty capability set, and `PERMISSION_DENIED`
for every rpc. To use the integration from a source build, either point it at your own Firebase project and set the two
keys to `true`, or use the debug build's **Feature flags** row to switch them on locally.

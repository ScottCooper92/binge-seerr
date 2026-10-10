# Data safety form

The answers for Play Console's *Data safety* section, each with the reason so a change to the app
can be checked against it. Play's definitions are the ones that matter here: data is **collected**
when it is transmitted off the device to the developer or to a third party the developer chooses,
and **shared** when it is transferred to a third party. Data the user sends to a destination they
chose themselves, such as their own server, is neither.

## Overview

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Crash reports go to Firebase Crashlytics, and usage data goes to PostHog once the user agrees. Both are the developer's chosen processors. Everything else goes to the server the user entered, or stays on the device. See the Plex note below. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Both SDKs send over HTTPS. The user's own server uses whatever scheme its address has, and is not collection. |
| Do you provide a way for users to request that their data is deleted? | **No** | Neither dataset is linked to a person, so there is nothing to look a request up by. Both can be turned off in Settings. |

## Data collected

| Data type | Collected | Shared | Optional | Purpose | Why |
|---|---|---|---|---|---|
| App activity: *App interactions* | Yes | No | Yes: asked on first launch, off unless granted | Analytics | The app's own screen names and action names (e.g. a request approved, a DVR instance saved), each with at most a fixed category as a param — never an id, a title or a message — to PostHog. A failed server action is also reported, with its action name, HTTP and gRPC status codes, and the server's software name and version. These describe the app's interactions, so *App interactions* still fits; the data is not a crash log. PostHog is a service provider processing on the developer's behalf. |
| App info and performance: *Crash logs*, *Diagnostics* | Yes | No | Yes: on by default, switch in Settings | App functionality | Crash reports to Firebase Crashlytics, a service provider. No user id; logs are not forwarded. A report carries a short action line from a fixed list and, for an action on one record, that record's number on the user's server (an issue, request, comment, TMDB, collection, instance or rule id), never a title, name, message or address. |
| Device or other IDs | Yes | No | Yes | Analytics, App functionality | The random per-install ids both SDKs mint. Not linked to an account. |

None of it is linked to the user's identity, and none is used for advertising.

The bug report form is not collection: it opens GitHub in the browser, and nothing is sent unless
the user submits the form.

## The Plex sign-in

When the user chooses to sign in with Plex, the app sends plex.tv a random per-install identifier
and polls for the sign-in's approval. plex.tv is the user's chosen identity provider for their own
server, reached only on the user's action, so this is the user sending data to a destination they
chose rather than the app sharing it. It is disclosed in `privacy.md` regardless. If Play's review
reads it as sharing, the entry would be: *Device or other IDs*, shared, optional, for *account
management*, with plex.tv as the recipient.

## The phone-to-TV hand-off

On a television the user can send their server's address from their phone. It crosses the user's own
local network between two devices of theirs, on the user's tap, and nothing reaches the developer
or a third party, so by Play's definitions it is neither collection nor sharing. It is disclosed in
`privacy.md` regardless, because it is the one place the server address (and, if the user switches it
on, the phone's sealed session) leaves a device other than through Block Store.

What a reviewer will want to check against the code: the phone sends only on the Send button
(`ui/handoff/SendAddressViewModel.kt`), over plain HTTP to the television's private address
(`handoff/AddressSender.kt`); a session, if sent, is sealed with a key that only the code on the
television's screen holds (`handoff/HandOffCrypto.kt`); the API key the phone stores is never sent
(`sharedSession()` returns only a session cookie); sign-in details typed on the phone for the
television's sign-in step, which can include an API key, are sealed the same way and sent on the
user's tap (`ui/handoff/SendSignInSteps.kt`); the television listens only on its own private
address, behind a one-time token and a PIN, through its sign-in step, and stops on connecting, on
leaving the page or after the sign-in timeout (`handoff/AddressHandOffListener.kt`). The scan uses Google Play services' scanner, so the
app holds no camera permission (`ui/handoff/ScanTvCode.kt`).

## Security practices

| Practice | Answer | Why |
|---|---|---|
| Data is encrypted in transit | Yes | Both SDKs use HTTPS. The user's own server is not collection. |
| You can request that data be deleted | No | Nothing collected is linked to a person. Disconnect or uninstall removes what is on the device. |
| Committed to the Play Families policy | No | Not a children's app. |
| Independent security review | No | |

## On-device data, for completeness

Not asked by the form, but the reader of `privacy.md` will want the list: the server address, one
encrypted secret (API key or session), a cache of what the server showed, notification choices,
the usage data and crash report choices, the Plex identifier, and up to three addresses per server
that were sent to a television (removed when the server is disconnected). All of it is on the device, and
Android's backup does not copy it (`allowBackup="false"`). The one exception is the connection
(address and secret), which is also held in Play services' Block Store; see the next section.

## The Block Store copy

The app keeps a copy of the connection in Google Play's Block Store so a new or restored device can
reconnect. It survives a device-to-device transfer and, only where end-to-end encryption is
available, a cloud restore (`auth/BlockStoreConnectionCarrier.kt`). It is Google's own platform
feature under the user's own Google account, not the developer receiving data, so it is not
collection by the developer. It is disclosed in `privacy.md` regardless. Whether the form should
list it is a question for the maintainer and Play's review; record the answer here once it is known.

## Permissions declared

The app's own manifest declares `INTERNET`, `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE` (a television
reads its own address on the network for the hand-off) and `ACCESS_LOCAL_NETWORK` (a runtime
permission from API level 37: reaching a server or a television on the user's own network; the app asks
when the user enters a local address or scans a television's code, and works without it for a public
address). Play reads the **merged**
release manifest, which also carries (checked against
`app/build/outputs/logs/manifest-merger-release-report.txt`, from `:app:processReleaseMainManifest`):

| Permission | Comes from |
|---|---|
| `ACCESS_NETWORK_STATE` | The app itself (the hand-off), and also WorkManager and PostHog |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | WorkManager |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | `androidx.biometric` (transitive; the app never uses biometrics) |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` (signature-level, the app's own) |

They are listed in `privacy.md`. Re-check the merged manifest whenever a dependency changes.

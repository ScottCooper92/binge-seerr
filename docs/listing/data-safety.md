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
| App activity: *App interactions* | Yes | No | Yes: asked on first launch, off unless granted | Analytics | The app's own screen names only, to PostHog, a service provider processing on the developer's behalf. |
| App info and performance: *Crash logs*, *Diagnostics* | Yes | No | Yes: on by default, switch in Settings | App functionality | Crash reports to Firebase Crashlytics, a service provider. No user id; logs are not forwarded. |
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
the usage data and crash report choices, and the Plex identifier. All of it on the device only, none of it backed up.

## Permissions declared

`INTERNET`, `POST_NOTIFICATIONS`. No others.

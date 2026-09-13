# Data safety form

The answers for Play Console's *Data safety* section, each with the reason so a change to the app
can be checked against it. Play's definitions are the ones that matter here: data is **collected**
when it is transmitted off the device to the developer or to a third party the developer chooses,
and **shared** when it is transferred to a third party. Data the user sends to a destination they
chose themselves, such as their own server, is neither.

## Overview

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Everything the app handles goes to the server the user entered, or stays on the device. Nothing reaches the developer or a third party of the developer's choosing. See the Plex note below. |
| Is all of the user data collected by your app encrypted in transit? | Not asked when nothing is collected | The app uses whatever scheme the entered address has; it warns on plain `http://` to a public host. |
| Do you provide a way for users to request that their data is deleted? | Not asked when nothing is collected | Disconnecting clears the stored server and secret; uninstalling removes everything, since `allowBackup="false"` keeps it out of cloud backups. |

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
| Data is encrypted in transit | Declared as above | Depends on the address the user entered. |
| You can request that data be deleted | Yes | Disconnect, or uninstall. |
| Committed to the Play Families policy | No | Not a children's app. |
| Independent security review | No | |

## On-device data, for completeness

Not asked by the form, but the reader of `privacy.md` will want the list: the server address, one
encrypted secret (API key or session), a cache of what the server showed, notification choices,
and the Plex identifier. All of it on the device only, none of it backed up.

## Permissions declared

`INTERNET`, `POST_NOTIFICATIONS`. No others.

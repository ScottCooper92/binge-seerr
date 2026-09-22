# Privacy policy

*Seerr Console for Binge* ("the app") is an administration console for a Seerr, Jellyseerr or
Overseerr server that you run. This policy describes what the app stores, where it sends it, and
what it does not do. It is written to be checked against the source code, which is public at
https://github.com/ScottCooper92/binge-seerr.

## What the app stores on your device

- **Your server's address**, as you entered it.
- **One secret for that server**: the API key you entered, or the session the server issued when
  you signed in with an account, with Plex, or with Jellyfin's Quick Connect. It is encrypted with
  a key held in the Android Keystore, so the key material never leaves the device's secure
  hardware, and it is stored only on the device.
- **A cache of what the server showed you** (requests, issues, users, titles and artwork URLs), so
  lists open instantly and work offline until refreshed. It is cleared when you disconnect.
- **Your notification choices**, and the last time the background check ran.
- **A random identifier** minted on first use, sent to plex.tv only when you sign in with Plex so
  that this install appears once, not once per sign-in, in the devices list on your Plex account.
- **Your answers about usage data and crash reports**, and whether shaking the phone offers to
  report a bug.

The app sets `allowBackup="false"`: none of the above is copied into a cloud backup, and
uninstalling the app removes all of it. Disconnecting inside the app removes the address, the
secret and the cache.

## Where the app sends data

- **To the server you entered**, and to nothing else, for everything the app shows and every change
  you make. If you enter a plain `http://` address, that traffic is unencrypted on the network; the
  app warns you when the address is on a public host. Use `https://` where you can.
- **To plex.tv**, only when you choose to sign in with Plex: the app opens Plex's sign-in page and
  polls plex.tv for the approval, sending the identifier above. Plex's own privacy policy governs
  what happens there.
- **To Binge**, on the same device, when Binge is installed and you allow it: request and status
  data for the titles Binge asks about, over an Android service binding that never leaves the
  device. Binge cannot read the server address or the secret.

- **To PostHog**, only if you agree to share usage data when the app first asks, or later in
  Settings: which of the app's screens you open. The screen names are the app's own ("requests",
  "settings"), never an id, a title, your server's address or anyone's name. Nothing is sent until
  you agree, and turning it off in Settings stops it. PostHog records it against a random
  identifier for this install, not against you, and derives a rough location from the IP address.
- **To Google (Firebase Crashlytics)**, when the app crashes: what the app was doing, the device
  model and Android version. This is on unless you turn it off in Settings. No user identifier is
  set, and the app does not forward its logs.
- **To GitHub**, only when you choose to report a bug: the app opens a new issue form in your
  browser with the app version, the device and the Android version filled in. Nothing is sent until
  you submit the form yourself, and the issue is public.

The app has no server of its own.

## What the app does not do

- No advertising or tracking libraries, and no analytics beyond the screen names above.
- No account with the app's author, and no sign-in other than to your own server (and Plex, if you
  choose it).
- No access to contacts, location, files, the camera, the microphone or the clipboard beyond the
  copy actions you press.
- No background activity other than the notification check, which asks your server for new
  requests and issues on a fixed interval, using the same stored secret, and which you can turn off.

## Permissions

- **Internet**, to reach your server.
- **Notifications** (Android 13 and later), to tell you about new requests and issues. Denying it
  disables the notifications and nothing else.

## Children

The app is a server administration tool and is not directed at children.

## Changes

This policy changes when the app's behaviour changes, in the same change, and its history is the
repository's history.

## Contact

Open an issue at https://github.com/ScottCooper92/binge-seerr/issues.

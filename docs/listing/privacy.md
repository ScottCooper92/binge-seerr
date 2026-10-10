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
  hardware, and it is stored on the device.
- **A copy of the connection in Google Play's Block Store**: the server address and the same
  secret, so that setting up a new device, or restoring one, can reconnect without signing in
  again. Block Store is part of Google Play services. The copy survives a device-to-device
  transfer, and is included in Google's cloud backup only where your Google account has end-to-end
  encryption available, so that Google cannot read it. It is best-effort, absent on a device
  without Play services, and removed when you disconnect in the app.
- **A cache of what the server showed you** (requests, issues, users, titles and artwork URLs), so
  lists open instantly and work offline until refreshed. It is cleared when you disconnect.
- **Your notification choices**, and the last time the background check ran.
- **Up to three addresses per server that you sent to a television** (see "Phone-to-TV hand-off"),
  so the next send offers them again. They are addresses you typed, nothing secret.
- **A random identifier** minted on first use, sent to plex.tv only when you sign in with Plex so
  that this install appears once, not once per sign-in, in the devices list on your Plex account.
- **Your answers about usage data and crash reports**, and whether shaking the phone offers to
  report a bug.

The app sets `allowBackup="false"`, so Android's own backup does not copy any of the above.
Uninstalling the app removes it. Disconnecting inside the app removes the address, the secret,
the cache and the Block Store copy. It does not remove the short list of addresses you sent to a
television; uninstalling, or clearing the app's data in Android's settings, does.

The connection can leave the device in two ways, both started by you: the Block Store copy, in the
two ways described above, and the phone-to-TV hand-off below, which stays on your local network.

## Where the app sends data

- **To the server you entered**, for everything the app shows and every change you make. If you
  enter a plain `http://` address, that traffic is unencrypted on the network; the app warns you
  when the address is on a public host. Use `https://` where you can.
- **To plex.tv**, only when you choose to sign in with Plex: the app opens Plex's sign-in page and
  polls plex.tv for the approval, sending the identifier above. Plex's own privacy policy governs
  what happens there.
- **To Google (Play services Block Store)**, if the device has Play services: the connection copy
  described above, handled by Google's own service under your Google account.
- **To a television on your local network**, only when you tap Send in the phone-to-TV hand-off
  described below.
- **To Binge**, on the same device, when Binge is installed and you allow it: request and status
  data for the titles Binge asks about, over an Android service binding that never leaves the
  device. Binge cannot read the server address or the secret.

- **To PostHog**, only if you agree to share usage data when the app first asks, or later in
  Settings: which of the app's screens you open, and which action you take (approving a request,
  resolving an issue, saving a DVR instance, and so on). Both are the app's own fixed names
  ("requests", "request_moderated"), with at most a fixed category as a detail (which kind of
  action, which sign-in method, on/off) — never an id, a title, a message, your server's address or
  anyone's name. When a change you make on the server fails, that is reported too: the app's own
  name for the action, the HTTP and gRPC status codes of the failure, and the server's software
  (Overseerr, Jellyseerr or Seerr) and its version. Nothing is sent until you agree, and turning it
  off in Settings stops it. PostHog
  records it against a random identifier for this install, not against you, and derives a rough
  location from the IP address.
- **To Google (Firebase Crashlytics)**, when the app crashes: what the app was doing, the device
  model and Android version. "What the app was doing" is a short line from a fixed list ("deleting
  issue", "moderating request: approved") and, where an action is on one record, that record's
  number on your server: the number of an issue, request, comment, title (its TMDB id), collection,
  DVR instance or override rule. The app attaches no title, name, message or server address. A crash
  report also carries the crash's own stack trace and exception message, which the app does not
  write and cannot promise are free of an address. This is
  on unless you turn it off in Settings. No user identifier is set, and the app does not forward its
  logs.
- **To GitHub**, only when you choose to report a bug: the app opens a new issue form in your
  browser with the app version, the device and the Android version filled in. Nothing is sent until
  you submit the form yourself, and the issue is public.

The app has no server of its own.

## Phone-to-TV hand-off

On a television the app can be told your server's address from your phone, so you do not type it
with a remote. It is only ever started by you.

- **On the television**, while it shows its code, the app listens for one phone on your local
  network: a small web server on the television's own private address (never on every network
  interface), at a one-time address that only the code on screen spells out, behind a PIN that the
  phone must send. It keeps listening through the television's sign-in step, and stops when the
  television connects, when you leave the page, or after the sign-in timeout; an unused code is
  replaced every five minutes, and five wrong PINs lock it. The page it serves is the one a phone's
  browser opens from the code.
- **On the phone**, you scan the code, or open its link, and tap Send. Nothing is sent before that
  tap. Scanning uses Google Play services' own code scanner: the app holds no camera permission,
  Play services shows the camera, and the app is given only the text of the code.
- **What crosses your network** is the server address you typed, the PIN, and, only if you switch
  it on in that sheet, your phone's session for that server, sealed with AES-256-GCM under a key
  that was in the code on the television's screen and is never sent over the network. The API key the
  app holds is never sent or shared.
- **On the television's sign-in step**, sign-in details you type on the phone, in the app or on the
  television's page, are sealed the same way and sent only when you tap. They are an email and
  password, a Jellyfin or Emby username and password, or an API key.
- **All of it** travels over plain HTTP, because a television has no certificate a phone would
  trust; someone watching your network sees the address and the sealed bytes, not what opens them.
- **After it is sent**, the phone remembers the address (up to three per server), so the next send
  offers it again.

## What the app does not do

- No advertising or tracking libraries, and no analytics beyond the screen names, action
  categories and failed-action details above.
- No account with the app's author, and no sign-in other than to your own server (and Plex, if you
  choose it).
- No access of its own to contacts, location, files, the camera, the microphone or the clipboard
  beyond the copy actions you press. The app holds no camera permission. When you choose to scan a
  television's code, Google Play services' scanner opens the camera for that one scan and hands the
  app the code's text.
- No background activity other than the notification check, which asks your server for new
  requests and issues on a fixed interval, using the same stored secret, and which you can turn off.

## Permissions

- **Internet**, to reach your server.
- **Notifications** (Android 13 and later), to tell you about new requests and issues. Denying it
  disables the notifications and nothing else.
- **Network state**, so a television can read its own address on your network for the hand-off.
- **Local network access** (a runtime permission from Android API level 37), to reach a server on
  your own network (a private address, a `.local` or `.lan` name) and a television for the hand-off.
  The app asks when you enter such an address or scan a television's code. Refusing it only turns
  those local-network features off; a server reached by a public address is unaffected.

Libraries the app includes add more to the installed app's manifest. None is used to reach anything
beyond what this policy describes:

- **Wake lock**, **run at startup** and **foreground service**, from Android's WorkManager (which
  also declares network state, as does PostHog): they let the background notification check
  survive a restart and run reliably.
- **Biometric** and **fingerprint**, declared by the `androidx.biometric` library that a
  dependency brings in. The app never asks for biometrics.
- A signature-level permission named after the app, added by `androidx.core` to protect the app's
  own broadcast receivers. No other app can hold it.

## Children

The app is a server administration tool and is not directed at children.

## Changes

This policy changes when the app's behaviour changes, in the same change, and its history is the
repository's history.

## Contact

Open an issue at https://github.com/ScottCooper92/binge-seerr/issues.

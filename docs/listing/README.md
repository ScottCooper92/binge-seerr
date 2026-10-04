# The Play listing

What the store page says, in the repository so it is reviewed like code and stays in step with
what the app does. Each file maps to one part of the Play Console.

| File | Play Console |
|---|---|
| [`listing.md`](listing.md) | Main store listing: app name, short description, full description (English) |
| [`listing.es.md`](listing.es.md) | The same, for the Spanish listing the app's `values-es/` earns it |
| [`privacy.md`](privacy.md) | The privacy policy the listing links to, published at https://scottcooper92.github.io/binge-seerr/privacy/ (see below) |
| [`data-safety.md`](data-safety.md) | The answers to the Data safety form, each derived from `privacy.md` and the code it describes |

## Where the privacy policy is published

Play needs the policy at a public URL. It is published at
https://scottcooper92.github.io/binge-seerr/privacy/, and that is the URL to give the Play Console.

The `privacy-policy.yml` workflow publishes it to GitHub Pages. It runs when a change to
`privacy.md` reaches `main`, renders the file to one HTML page, and deploys it, so a merged policy
change goes live with no further step. It publishes that one page, not the rest of `docs/`. The
page loads nothing from any other site, and its last-updated date is the date of the last commit
to `privacy.md`. To publish again without a change, run the workflow by hand from the Actions tab.

## What is not here

**Screenshots and the feature graphic.** These are Play Store listing captures, a different thing
from the `@PreviewTest` screenshot suite CLAUDE.md's Gates section gates — nothing captures these
automatically, so the frames come from the app on a device. Capture, in this order, on a phone at
1080×1920 or larger:

1. The hub, connected, with a pending count, a downloading strip and the four sections.
2. The requests browser with the filter band and a few rows in mixed states.
3. A request page with the moderation sheet open.
4. The issues browser, and an issue thread.
5. The users browser and a user's page.
6. A settings page with real values (blur the server address).
7. Binge's title page with this app's request button, for "what Binge adds".

Optionally two Android TV frames (1920×1080): the rail with the hub board, and a list with its
action sheet. The feature graphic (1024×500) is a design asset; it does not exist yet.

**The app name is a maintainer's decision.** `listing.md` proposes one and says why. The
in-app label stays `Seerr` (`companion_name`), which is what Binge shows for this integration.

## Keeping it true

The listing describes behaviour, so a change to what the app stores, sends or asks for is a change
to `privacy.md` and `data-safety.md` in the same PR. The specific claims that must stay true:

- The one secret (an API key or a session cookie) is encrypted with an Android Keystore key
  (`auth/SecretCipher.kt`) and stored on the device. A copy of the connection (address and secret)
  also goes to Google Play's Block Store (`auth/BlockStoreConnectionCarrier.kt`), which survives a
  device-to-device transfer and, where end-to-end encryption is available, a cloud restore.
  Disconnecting clears both. So it is **not** true that the secret never leaves the device.
- Traffic goes to the server the user entered; plex.tv when the user chooses to sign in with Plex;
  PostHog only after the user agrees to usage data; Firebase Crashlytics for crash reports unless
  the user turns them off; and GitHub only when the user submits the bug report form.
- There is an analytics SDK (PostHog) and a crash reporting SDK (Firebase Crashlytics), both gated
  as above, and no advertising SDK. Check `app/build.gradle.kts` and `telemetry/` before changing
  this claim in either direction.
- `android:allowBackup="false"`: Android's own backup does not copy the app's data, and uninstalling
  removes it. Block Store is a separate mechanism and the one path by which the connection can
  leave the device.

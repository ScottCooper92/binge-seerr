# The Play listing

What the store page says, in the repository so it is reviewed like code and stays in step with
what the app does. Each file maps to one part of the Play Console.

| File | Play Console |
|---|---|
| [`listing.md`](listing.md) | Main store listing: app name, short description, full description (English) |
| [`listing.es.md`](listing.es.md) | The same, for the Spanish listing the app's `values-es/` earns it |
| [`app-access.md`](app-access.md) | App content > App access: how a reviewer uses the app with no server (the demo) |
| [`privacy.md`](privacy.md) | The privacy policy the listing links to, published at https://scottcooper92.github.io/app-feedback/seerr/privacy/ (see below) |
| [`data-safety.md`](data-safety.md) | The answers to the Data safety form, each derived from `privacy.md` and the code it describes |

## Where the privacy policy is published

Play needs the policy at a public URL. It is published at
https://scottcooper92.github.io/app-feedback/seerr/privacy/, and that is the URL to give the Play Console and the one the app's
privacy row opens. The page lives in the public app-feedback repository, because this repository is private.

`privacy.md` here is the source of the policy, and it is reviewed with the code. The `privacy-policy.yml` workflow runs when a change to
`privacy.md` reaches `main` and opens a pull request on app-feedback that replaces the body of `docs/seerr/privacy/index.md`. The page's
front matter and its last-updated line are kept, and the date in that line is the date of the last commit to `privacy.md`. The policy goes
live when that pull request merges. A second change before then updates the same pull request. To sync again without a change, run the
workflow by hand from the Actions tab.

**The old page.** Builds released before the policy moved open https://scottcooper92.github.io/binge-seerr/privacy/ from their About row.
That site is not unpublished on purpose, since it would answer them with a 404, and it is not left as a copy that goes stale. The
`privacy-redirect.yml` workflow replaces it with `legacy-privacy-redirect.html`, a page with a meta refresh, a canonical link and a
visible link to the new URL (GitHub Pages cannot send an HTTP redirect). It deploys when that file or the workflow changes on `main`,
or when run by hand. It needs Pages on with GitHub Actions as its source, and the `github-pages` environment kept. A maintainer sets
the Play Console privacy policy field to the new URL, which is #478.

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

Optionally two Android TV frames (1920×1080): Home with the hub board, and a list with its
action sheet. The feature graphic (1024×500) is a design asset; it does not exist yet.

**The app name is a maintainer's decision.** `listing.md` records it and says why. The
label Binge shows for this integration stays `Seerr` (`companion_label`, which the manifest's
companion metadata names). The launcher label is `app_label`, "Seerr Companion".

## Keeping it true

The listing describes behaviour, so a change to what the app stores, sends or asks for is a change
to `privacy.md` and `data-safety.md` in the same PR. The specific claims that must stay true:

- The one secret (an API key or a session cookie) is encrypted with an Android Keystore key
  (`KeystoreSecretCipher` from the shared library, built in `di/DeviceKeysModule.kt`) and stored on the device. A copy of the connection (address and secret)
  also goes to Google Play's Block Store (`auth/BlockStoreConnectionCarrier.kt`), which survives a
  device-to-device transfer and, where end-to-end encryption is available, a cloud restore.
  Disconnecting clears both. So it is **not** true that the secret never leaves the device.
- Traffic goes to the server the user entered; plex.tv when the user chooses to sign in with Plex;
  TMDB's image servers for posters; the host of a user's picture address, as the server reports it
  (often Gravatar or plex.tv); Firebase Remote Config for four on/off settings, always;
  PostHog only after the user agrees to usage data; Firebase Crashlytics for crash reports and recovered failures only if
  the user agrees on first run; and GitHub only when the user submits the bug report form.
- The phone-to-TV hand-off (`handoff/`, `ui/handoff/`) sends the server address, and a sealed session
  only if the user switches it on, to a television on the same network, only on the Send button. The
  API key the phone stores is never sent; sign-in details typed for the television's sign-in step
  (email and password, username and password, or an API key) are sealed and sent on the user's tap,
  and the listener stays up through that step. The scanner is Google Play services', so the app holds no camera permission.
  The addresses it remembers (`handoff/HandOffAddressMemory.kt`) are cleared on disconnect and on
  connecting to a server (`onServerChanged` in `di/AuthModule.kt`).
- A crash or recovered-failure report carries the ids of the record in hand (attached at the `crashBreadcrumbs.key(...)` call sites, for example in `ui/issues/IssueDetailViewModel.kt`, through the shared `CrashBreadcrumbs`), a fixed action
  line, the device model and the Android version. The app attaches no title, name, message or address; the crash's own stack trace
  and exception message are Crashlytics's and are not filtered.
- There is an analytics SDK (PostHog) and a crash reporting SDK (Firebase Crashlytics), both gated
  as above, and no advertising SDK. Check `app/build.gradle.kts` (the `app-services-posthog` and
  `app-services-firebase` dependencies and the Crashlytics plugin) and `di/TelemetryModule.kt` before changing this claim in
  either direction.
- `android:allowBackup="false"` and `android:dataExtractionRules` (`res/xml/data_extraction_rules.xml`):
  Android's own backup and device-to-device transfer do not copy the app's data, and uninstalling
  removes it. `allowBackup` alone does not stop a device-to-device transfer on every Android 12+
  device, so keep both. Block Store is a separate mechanism, and with the phone-to-TV hand-off (above) one of
  the two ways the connection can leave the device.

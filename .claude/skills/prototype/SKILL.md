---
name: prototype
description: Fast on-device iteration on a UI or behaviour change — edit, build the debug APK, install on the maintainer's devices, repeat — with no tests, screenshots, lint or commits until the change is agreed, then promote it to a PR properly. Use when asked to "prototype", "try", "put it on the phone/TV", or to iterate on how something looks or feels on a device.
---

# Prototype on a device

A prototype is a conversation with the maintainer through their devices. The loop is the product:
**change → build → install → they look → change**. Every minute between a comment and the new build on the
device is cost.

## The loop

1. **Work in a worktree**, never by switching the main clone's branch: `git worktree add ../<topic>-proto
   origin/main`, then `git submodule update --init` (the design system and companions are submodules) and copy
   `local.properties` from the main clone.
2. **Build the APK and nothing else:** `./gradlew -q :app:assembleDebug`. No unit tests, no screenshot
   re-records or validation, no ktlint/detekt/lint, no `./gradlew build`, no commits — not until the
   maintainer says the change is right. A compile error is the only check that runs. A round with screenshots
   and the full build takes 10–15 minutes; without them it takes 1–2.
3. **Install, don't drive:** `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`. The
   debug build has its own id (`io.github.scottcooper92.binge.seerr.debug`), so it sits beside a release
   install. The maintainer usually has the device in hand: don't launch, tap or screenshot unless asked.
4. **Say what changed and what to look at**, in a few lines, then wait.
5. **Shared design-system changes** go in the `design-system/` submodule checkout while prototyping; they
   become their own binge-design-system PR afterwards.

Answer questions ("should we…?", "is it possible…?") with a recommendation before building.

Phone and TV both matter: a change to a flow that spans them (a TV screen and the phone sheet it hands off
to) needs the same build on both devices.

## Builds on a shared machine

One Gradle invocation at a time, and `./gradlew --stop` when a round is done. If the machine also runs CI
jobs, don't build alongside one — wait for it to finish rather than stopping it.

## Promoting it

When the change is agreed:

1. Snapshot the prototype (`git diff`, the submodule's diff and any new files) so the device build can be
   reproduced exactly.
2. Turn it into a PR: strip prototype comments, use dimens and constants, add the Spanish strings and
   `./gradlew updateTranslationHashes`, add unit tests and screenshot frames, and run `./gradlew build` once.
   Design-system changes go to binge-design-system first, with this repo's submodule pinned to that PR.

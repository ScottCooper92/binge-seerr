# The device smoke

`device-smoke.yml` installs the minified debug build (`-PminifyDebug`) and its test APK on an
emulator and runs `CompanionServiceRoundTripTest`. It proves the release keep rules hold across a
real Binder: the exported Service, the generated stubs and the protobuf runtime.

## A crashed run can read as green

If the instrumentation process crashes before it finds a test, AGP's managed-device task still
exits 0. It records one case, `testRunFailed`, in `TEST-smokeAtdApi34.xml` and finishes in about
two seconds. The lane therefore checks that each test it exists to run is in the results
(`expected_tests`), and a run that records none fails. Do not remove that check.

Found in #2648: the lane had been green on `main` without running a test, because the test side
needs classes and methods the minified app removes.

## Why the test run needs its own keep rules

The test APK resolves every class and method it does not define from the **app's** dex, and
`-PminifyDebug` shrinks the app first. R8 removes whatever the app never uses, and a test, or the
runner or a library under it, that reaches one dies with `NoClassDefFoundError` or
`NoSuchMethodError`.

Two files hold the keeps:

- `app/src/main/keepRules/instrumentation.keep`: what the AndroidX test runner needs in `onCreate`.
  It is in `main` so the minified debug build and release shrink against the same rules.
- `app/src/debug/keepRules/smoke.keep`: what the smoke test and its libraries (MockWebServer,
  the Hilt test root, the client half of gRPC) reach. It is debug-only, so release shrinks as before.
  Each rule is exact, with the reason beside it.

## Finding the next one

The lane records logcat (`capture_logcat`) and uploads it with the results as
`device-smoke-logcat.txt`. The crash is the `FATAL EXCEPTION` for the app's package, or a failed
test's stack in `TEST-smokeAtdApi34.xml`. That names one class or method at a time. To find the
whole set, build both APKs and intersect two lists:

1. What R8 removed from the app: `app/build/outputs/mapping/debug/usage.txt`. A line ending in
   `:` is a class with removed members indented under it; a bare name is a removed class.
2. What the test APK references: the type descriptors in its dex for classes, and
   `dexdump -d` (from build-tools) for methods and fields. Unzip the dex from
   `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Anything the test references that `usage.txt` says was removed needs a keep. An `apkanalyzer dex
packages` comparison of the two APKs misses methods and some classes, so do not rely on it alone.

R8 rule syntax has one trap: `**` as a return type does not match a primitive, so a keep written
`** closeQuietly(...)` silently ignores a method that returns `void`. Use `***`.

Do not upload the built APKs or the mapping as a CI artifact: they hold classes from private
libraries, and this repository is public.

Plain Java calls in a test avoid part of the problem: a Kotlin extension on a collection, string or
array (`single()`, `first()`, `encodeToByteArray()`) resolves to a stdlib facade the app may not
keep.

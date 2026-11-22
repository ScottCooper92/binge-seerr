# The device smoke

`device-smoke.yml` installs the minified debug build (`-PminifyDebug`) and its test APK on an
emulator and runs `CompanionServiceRoundTripTest`. It proves the release keep rules hold across a
real Binder: the exported Service, the generated stubs and the protobuf runtime.

## A crashed run can read as green

If the instrumentation process crashes before it finds a test, AGP's managed-device task still
exits 0. It records one case, `testRunFailed`, in `TEST-smokeAtdApi34.xml` and finishes in about
two seconds. The lane therefore checks that each test it exists to run is in the results
(`expected_tests`), and a run that records none fails. Do not remove that check.

Found in #2648: the lane ran green on `main` without running a test, because of the first cause
below.

## Why the test run needs its own keep rules

The test runner resolves Kotlin stdlib classes from the **app's** dex, and R8 removes any facade
the app never calls. A test, or the runner itself, that reaches one dies with
`NoClassDefFoundError` before it starts. The keeps are in `app/src/main/keepRules/instrumentation.keep`.
They are in `main` so the minified debug build and release shrink against the same rules.

To find the next one, read the emulator's logcat: the lane uploads it as `app/build/logcat` with the
results. The crash is the `FATAL EXCEPTION` for the app's package. Keep the exact class, with its
reason in the file, not a package sweep.

Plain Java calls in a test avoid the problem: a Kotlin extension on a collection, string or array
(`single()`, `first()`, `encodeToByteArray()`) resolves to a stdlib facade the app may not keep.

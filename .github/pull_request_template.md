## What and why

<!-- One short paragraph, then `Closes #N` (or `Part of #N`). Say what a change to a public signature means for consumers. -->

## Checked before pushing

<!-- Tick what you ran locally on the final head. If you couldn't run one (it runs out of memory, or needs the network), leave it unticked and say so. Don't tick a box you didn't run. -->
- [ ] `./gradlew ktlintCheck detekt`
- [ ] `./gradlew checkTranslationStaleness :app:lint` (after any string change, `updateTranslationHashes`, with each translation re-read)
- [ ] `./gradlew :app:testDebugUnitTest koverVerify`
- [ ] `./gradlew checkGrpcAlignment`, if a dependency or a submodule pin moved
- [ ] Re-recorded screenshots viewed: all of them, or every one checked by script and a sample by eye (say which)
- [ ] Left to CI, and why: <!-- e.g. the full validateDebugScreenshotTest runs out of memory locally -->

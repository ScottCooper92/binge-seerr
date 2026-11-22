#!/usr/bin/env bash
# Tests for classify-changes.sh: each case is a diff and the three answers it must give.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
failed=0

# check <name> <expected "product screens release"> <path>...
check() {
  local name="$1" expected="$2"
  shift 2
  local file got
  file="$(mktemp)"
  : > "$file"
  for path in "$@"; do printf '%s\0' "$path" >> "$file"; done
  got="$("$here/classify-changes.sh" "$file" 2>/dev/null | cut -d= -f2 | tr '\n' ' ' | sed 's/ $//')"
  rm -f "$file"
  if [ "$got" != "$expected" ]; then
    echo "FAIL $name: expected '$expected', got '$got'"
    failed=1
  fi
}

K=app/src/main/kotlin/com/cooper/binge/companion/seerr
check "docs only"                  "false false false" CLAUDE.md docs/listing/privacy.md .ai/agents/pr-review-guide.md
check "the Play release notes"    "false false false" distribution/whatsnew/whatsnew-en-GB
check "scripts and tooling only"   "false false false" .github/scripts/classify-changes.sh tools/x.sh
check "the shard script runs screenshots" "true true false" .github/scripts/shard-screenshots.sh
check "the R8 name check runs in release" "true false true" .github/scripts/check-r8-names.sh
check "workflows run every Gradle job, R8 too" "true true true" .github/workflows/ci.yml
check "empty diff runs everything" "true true true"
check "a screen"                   "true true false"   "$K/ui/hub/HubScreen.kt"
check "a frame"                    "true true false"   app/src/screenshotTestDebug/reference/x.png
check "server code can reach UI"   "true true false"   data/src/main/kotlin/com/cooper/binge/companion/seerr/seerr/SeerrApi.kt
check "a resource string"          "true true false"   app/src/main/res/values/strings.xml
check "tests only"                 "true false false"  app/src/test/kotlin/x/YTest.kt
check "the data module's tests"     "true false false"  data/src/test/kotlin/x/YTest.kt
check "the shared test support"    "true false false"  core/testing/src/main/kotlin/x/Fake.kt core/testing/src/test/kotlin/x/FakeTest.kt
check "its build file runs R8 too" "true true true"    core/testing/build.gradle.kts
check "the exported Service"       "true false false"  "$K/service/SeerrCompanionService.kt"
check "notifications"              "true true false"   "$K/notifications/NotificationPrefs.kt"
check "the review launcher"        "true false false"  "$K/review/ReviewPromptLauncher.kt"
check "hashes only"                "true false false"  translation-hashes.txt
check "the version catalog"        "true true true"    libs.versions.toml
check "a build file"               "true true true"    app/build.gradle.kts
check "a keep rule"                "true false true"   app/src/main/keepRules/app.pro
check "a pro file outside keepRules" "true true true" app/proguard-rules.pro
check "the release source set"     "true true true" app/src/release/kotlin/X.kt
check "assets"                     "true true true" app/src/main/assets/x.json
check "the manifest"               "true true true"    app/src/main/AndroidManifest.xml
check "the data module's manifest"  "true true true"    data/src/main/AndroidManifest.xml
check "the data module's build file" "true true true"   data/build.gradle.kts
check "the design system pin"      "true true true"    design-system
check "the homelab-core pin"      "true true true"    homelab-core
check "the companions pin"         "true true true"   binge-companions
check "docs with a screen"         "true true false"   CLAUDE.md "$K/ui/hub/HubScreen.kt"

check "a UI-only change skips R8" "true true false" "$K/ui/hub/HubScreen.kt" app/src/main/res/values/strings.xml
check ".gitmodules runs R8" "true true true" .gitmodules
check "a Gradle wrapper change"     "true true true"  gradle/wrapper/gradle-wrapper.properties
check "gradle.properties"           "true true true"  gradle.properties
check "settings.gradle.kts"         "true true true"  settings.gradle.kts
check "the binge-ci pin"            "true false true" binge-ci
check "a UI change with a build file" "true true true" "$K/ui/hub/HubScreen.kt" app/build.gradle.kts

# Paths git would quote in its default output: a non-ASCII character, a tab or a newline. The list is NUL-separated, so
# each reads as itself and matches its pattern. A keep rule is the release set's only R8 input here.
check "an accented keep-rule name"   "true false true" "app/src/main/keepRules/règles.pro"
check "a tab in a path"              "true false true" $'app/src/main/keepRules/a\tb.pro'
check "a newline in a path"          "true false true" $'app/src/main/keepRules/a\nb.pro'

# A rename lists both its paths when the diff is read as CI reads it, so a source file moved into docs still reads as a
# source change. With git's default rename detection only the new path would print, and the diff would look docs-only.
repo="$(mktemp -d)"
(
  cd "$repo"
  git init -q
  mkdir -p app/src/main/kotlin docs
  printf 'class X\n' > app/src/main/kotlin/X.kt
  git add -A
  git -c user.email=t@example.invalid -c user.name=t commit -q -m base
  git mv app/src/main/kotlin/X.kt docs/X.kt
  git -c user.email=t@example.invalid -c user.name=t commit -q -m move
  git diff -z --no-renames --name-only HEAD~1 HEAD > "$repo/changed.list"
)
got="$("$here/classify-changes.sh" "$repo/changed.list" 2>/dev/null | cut -d= -f2 | tr '\n' ' ' | sed 's/ $//')"
rm -rf "$repo"
[ "$got" = "true true false" ] || { echo "FAIL a source file renamed into docs: expected 'true true false', got '$got'"; failed=1; }

[ "$failed" -eq 0 ] && echo "classify-changes: all cases pass"
exit "$failed"

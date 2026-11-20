#!/usr/bin/env bash
# Decides which CI jobs a diff can affect, from the changed paths in the file given as $1, NUL-separated
# (`git diff -z --no-renames --name-only`: a rename lists both paths, and no path is quoted). Prints `product=`, `screens=` and `release=`, each true or false, one per line.
#
# The design is Binge's change classifier, kept deliberately small. A job is skipped only when
# EVERY changed path is in a closed, finite set that provably cannot affect it. For `product` and
# `screens` a path in no set runs the job. `release` is the reverse, an allow-list: it runs only for a
# listed input, so a new R8 input (a new build file, a keep rule directory) must be added to
# `release_input`. An empty list runs everything, and so does anything this script cannot read. The
# suite on main and the nightly never call this, so a wrong skip is found at the next merge.
#
#   product  the Gradle jobs: checks (lint, detekt, ktlint, assemble) and tests. False only when
#            every path is documentation or tooling nothing compiles or packages.
#   screens  the screenshot frames. False when no path can move a frame: tests, the exported
#            Service, the review launcher, keep rules and the like.
#   release  the release variant's build: R8 and whether the release source set compiles. True only for
#            what only that build reads: a build file (any *.gradle.kts), the version catalog, `gradle/` and
#            gradle.properties, a keep rule or any .pro file, the manifest, `res/xml`, `res/raw` and assets,
#            the release source set, .gitmodules, a submodule pin (design-system, binge-companions, binge-ci,
#            homelab-core) or a workflow file. Binge builds release only on a tag; here a PR builds it only for
#            those paths, and every push to main builds it regardless. A change to `src/main` code can still
#            break the release-only compile, and a push to main is what catches that. Ordinary app code and
#            UI changes skip it.
#
# Never state a skip-set path here that a bot reads or that the build reads. `.github/workflows`
# is in no set: a change to the pipeline is validated by running the pipeline.
set -euo pipefail

list="${1:?usage: classify-changes.sh <file listing the changed paths>}"

# Paths no Gradle task reads.
non_product() {
  case "$1" in
    # The screenshots job runs this script, so it is an input to the build.
    .github/scripts/shard-screenshots.sh) return 1 ;;
    *.md | LICENSE* | docs/* | .ai/* | tools/* | .gitignore | .github/ISSUE_TEMPLATE/* | .github/renovate.json5 | .github/scripts/*) return 0 ;;
    # seerr only: the Play release notes, read by release.yml and by no Gradle task.
    distribution/whatsnew/*) return 0 ;;
  esac
  return 1
}

# Paths of product code that cannot move a screenshot frame.
no_frames() {
  case "$1" in
    app/src/test/* | app/src/androidTest/* | app/src/debug/keepRules/* | app/src/main/keepRules/*) return 0 ;;
    # The shared test support module is test code only: no screenshot test depends on it.
    data/src/test/* | core/testing/src/*) return 0 ;;
    # seerr only: this app's package.
    app/src/main/kotlin/com/cooper/binge/companion/seerr/service/*) return 0 ;;
    app/src/main/kotlin/com/cooper/binge/companion/seerr/review/*) return 0 ;;
    translation-hashes.txt | detekt.yml | .editorconfig | binge-ci) return 0 ;;
  esac
  return 1
}

# Paths that only the release variant reads, or that decide whether R8 can build the app.
release_input() {
  case "$1" in
    *.pro | app/src/main/keepRules/*) return 0 ;;
    *.gradle.kts | libs.versions.toml | gradle/* | gradle.properties) return 0 ;;
    design-system | binge-companions | binge-ci | homelab-core | .gitmodules) return 0 ;;
    */src/release/* | */src/main/AndroidManifest.xml | */src/main/res/raw/* | */src/main/res/xml/* | */src/main/assets/*) return 0 ;;
    # The pipeline is validated by running it, release job included.
    .github/workflows/*) return 0 ;;
  esac
  return 1
}

# The list is NUL-separated (`git diff -z`): git quotes a path with a non-ASCII character, a quote or a tab
# in its default output, and that quoted form would match no pattern below.
paths=()
while IFS= read -r -d '' path; do
  [ -n "$path" ] && paths+=("$path")
done < "$list"

if [ "${#paths[@]}" -eq 0 ]; then
  echo "No changed paths to read: running everything." >&2
  printf 'product=true\nscreens=true\nrelease=true\n'
  exit 0
fi

product=false screens=false release=false
for path in "${paths[@]}"; do
  non_product "$path" && continue
  product=true
  no_frames "$path" || screens=true
  release_input "$path" && release=true
done

printf 'product=%s\nscreens=%s\nrelease=%s\n' "$product" "$screens" "$release"

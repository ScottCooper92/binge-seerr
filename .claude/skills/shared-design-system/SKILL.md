---
name: shared-design-system
description: Use when changing anything under this app's ui/ package or the design-system submodule — the UI is built from the design system Binge and the companions share, so a change may belong there, and a change there reaches this app only through a submodule bump. Also use when asked whether this app's UI has drifted from Binge's.
---

# The design system is a shared repository

This app's screen is built from
[binge-design-system](https://github.com/ScottCooper92/binge-design-system), checked out at
`design-system/` as a git submodule and included as a composite build (`com.binge:designsystem`,
package `com.binge.designsystem`). The theme, the components and the dp tokens come from there;
this repository holds only what is this app's own — its strings, its screen, its ViewModel.

That replaces the copying an earlier `design-system-provenance.tsv` recorded. Nothing is copied
any more, so there is no drift to track: the submodule pin says exactly which design system this
app was built against.

## Which side a change belongs on

- **A component, a token, the theme** — a PR on binge-design-system. Its one rule is that a
  component there may not know what Binge's data looks like, and this app is the proof: if it
  needs something a companion could not use, it does not belong there.
- **Then bump the submodule here in a commit of its own.** `.gitmodules` and the pin are
  agent-governed paths, so an author bot's commit can revert them — check the pin after any bot
  push, exactly as for `binge-integrations/`.
- **The screen itself** — an ordinary change here. Reach for a design-system component before
  writing chrome, and for its `padding_*` tokens (`import com.binge.designsystem.R as DesR`)
  before adding a dimen of your own. A dimen this repository declares is a sign the component
  it sizes wants to be shared.

## Versions

A consumer that includes a build has to agree with it on Compose and AGP, and this one pins
`material3` ahead of the BOM to the version the design system pins. A bump there is a bump in
`libs.versions.toml` here, in the same submodule-bump commit.

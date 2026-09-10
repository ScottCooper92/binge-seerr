---
name: shared-design-system
description: Use when editing, adding or removing any file listed in design-system-provenance.tsv — the UI copied from Binge so the two apps look like one product. Also use when copying a new file across from Binge, or when asked whether this app's UI has drifted from Binge's.
---

# Shared design-system files

Some of this app's UI is **copied from Binge** rather than written here, so a user moving between
the two apps sees one product. `design-system-provenance.tsv` at the repository root is the record
of which files those are and where each came from.

Copying was chosen over a shared module deliberately: a submodule is a lot of machinery for the
~23 files one companion needs, and this repository is meant to be readable standalone by someone
writing their own companion. The cost is drift. This skill is how drift stays visible.

## If you changed a file listed in the manifest

**Divergence is allowed. Silent divergence is not.**

You do not need permission to change a copied file — this app's needs are not Binge's, and forcing
them to stay byte-identical would be worse than letting them differ. What you must do is say so:

1. Say in the PR body that the file is manifest-listed and what you changed about it.
2. Say whether the change is **this app's own** (a fit for something Binge does not have) or a
   **general improvement** that Binge would want too.
3. If it is the second, file an issue on Binge rather than assuming someone will notice. A fix
   made here and not there is the exact failure this manifest exists to surface.

Do **not** update the `binge-commit` or `sha256` columns for an edit made here. Those describe
where the file came FROM, not what it has become. Rewriting them to match a local edit erases the
one fact the row carries.

## If you are copying a NEW file across from Binge

Add a row. Four tab-separated columns, and the digest is of **Binge's** file at the commit copied
from, not of the local copy:

```sh
# from a Binge checkout, at the commit you are copying
git rev-parse HEAD
git show HEAD:core/designsystem/src/main/kotlin/.../Foo.kt | sha256sum
```

Adding a row is a decision, not bookkeeping. It says this file is UX that should stay consistent
across apps. A file that is genuinely this app's own does not belong in the manifest at all — and
a file added without a row is invisible to every check on both sides.

## Checking whether Binge has moved on

**You probably cannot do this from here, and should not pretend otherwise.**

Binge is a private repository. Nothing running in this repository's CI can read it, and an
unauthenticated fetch returns 404 rather than anything you can act on. The check runs from the
Binge side, where this repository is public and readable — see Binge's skill of the same name.

From a local machine that has both checked out, compare directly:

```sh
# in the Binge checkout
git show <binge-commit>:<path-in-binge> | sha256sum   # should equal the manifest's sha256
git show HEAD:<path-in-binge> | sha256sum             # differs => Binge has moved on since
```

If you cannot reach Binge, **say that** rather than reporting the files as in sync. "I could not
check" and "they match" are different answers and only one of them is true.

## Two files that cannot simply be copied

`MediaAvailabilityUi.kt` and `AccountActionSnackbarHandler.kt` are coupled to Binge's
`core:domain` types. Both are integration-shaped — they render request state — so what they want
is the contract's generated types, not Binge's internal ones. That is the SDK's job
(ScottCooper92/Binge#1786), not something to solve by copying a domain model across.

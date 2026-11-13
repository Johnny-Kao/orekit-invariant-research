# Orekit invariant research

Independent research repository (not a fork of Orekit).

## Source provenance

- Official upstream: https://gitlab.orekit.org/orekit/orekit
- Read-only GitHub mirror: https://github.com/CS-SI/Orekit
- Snapshot supplied locally: `~/Downloads/orekit-develop.zip`
- ZIP SHA-256: `bc910918e42557423f2180697bb81656391fabefa9c40029d8811c69ecc6ff90`
- Snapshot inventory: 5,525 files; `orekit-develop/pom.xml`; Java release 21
- CI baseline: mirror commit `90d9b4bdda78891e451d881afa3c11ac9645ac42`

The local ZIP is an input for source inventory. It is **not** committed to this repo. The CI fetches the pinned mirror commit, rather than testing the macOS worktree. The archive and the pinned commit have not yet been proven byte-for-byte identical.

## First hypothesis

Audit event detection and propagation invariants, including event handlers that reset spacecraft state, forward/backward propagation, and close consecutive events. **No bug is claimed at this stage.**

## Validation

Run the workflow **Orekit event baseline (pinned upstream)** using GitHub Actions. It checks out the exact upstream SHA, uses Temurin JDK 21, and runs a focused set of *upstream's own tests*. Uploads Surefire XML reports even on failure.

The initial run establishes the upstream baseline only. Follow-up independent regression tests must be added before treating any candidate as confirmed.

No upstream issues, merge requests, branches, or staging repositories are modified.

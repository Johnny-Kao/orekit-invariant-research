# SP3Writer-only preflight — Research A/B comparison

## Scope

This is an independent alternative to the existing `SP3.validate(false)` guard, based on Orekit `90d9b4bdda78891e451d881afa3c11ac9645ac42`. GitLab personal Fork Staging `research/sp3-epoch-collision-staging` and the official upstream are **untouched**.

## A vs B

| Topic | A: public validate guard (current GitLab staging) | B: Writer-only preflight (this research) |
|---|---|---|
| Production files | SP3.java, OrekitMessages.java, 11 localization resources | SP3Writer.java only |
| Production diff | +72/−1 (13 files) | +51/−9 (1 file; 9 lines moved to shared formatter) |
| Public `SP3.validate(false)` | Narrowed acceptance of some formerly valid objects | Unchanged |
| Output epoch count mismatch | Rejects collisions before output | Simulates actual Writer schedule and rejects before output |
| Same-satellite legitimate 0.5 s epochs | Accepted | Accepted |
| Repeated epoch / collision cancellation | A cross-satellite guard rejects the tested combination | Count-only B initially missed it; **fixed by ordered epoch verification** |
| Distinct timestamps with identical 8-digit printed representation | A initially missed this edge | **Fixed by comparing exact formatted output epochs** |
| Error messages / localization | New `SP3_EPOCHS_TOO_CLOSE` and 11 resources | Reuses `SP3_NUMBER_OF_EPOCH_MISMATCH` and `INCONSISTENT_SAMPLING_DATE` |
| Extra time in normal path | `O(E)` on existing sorted set | One additional `O(E×S + C)` epoch scheduling pass, including epoch formatting |
| Extra memory | `O(1)` | `O(S)` iterator objects |
| Maintenance | More state, wider API effect | One-file change; scheduling predicate remains duplicated with actual Writer |

E = number of epochs; S = satellites; C = coordinates. B simulates the exact existing iterator-consumption schedule *before writing the header*. It also verifies output times are strictly increasing and distinct after formatting to the SP3 8-decimal precision. The same `formatEpoch` helper is used by both preview and real output to avoid formatting drift.

B preserves `SP3.validate(false)` semantics and does not modify Writer's existing selection of coordinates. It is **not performance-neutral**: it deliberately computes the schedule twice.

## Functional verification

macOS Java 21, pinned Orekit 14.0-SNAPSHOT and Hipparchus 4.1-SNAPSHOT:

- Command: `mvn -B -ntp '-Dtest=SP3*Test,OrekitMessagesTest' -DfailIfNoTests=true test`
- Result: **108 tests passed; 0 failures/errors** (including one research Writer benchmark test and one validation benchmark test)
- `mvn -B -ntp -DskipTests checkstyle:check`: **0 violations**
- Cross-satellite 0.5 s and 1 ns offset: rejection before any output
- Same-satellite consecutive 0.5 s: two valid output epochs / Parser round-trip
- Duplicate same-satellite time masking a cross-satellite collapse: rejected (test was RED with count-only B)
- Same-satellite 1 ns distinct dates that print identically: rejected (test was RED before formatting check)
- Identical times across satellites: one valid epoch
- 900 s standard and 900.5 s allowed offset: round-trip

Only a localized Research copy was modified; Staging was not updated.

## Approximate Writer benchmark

Synthetic `SP3` with regular epochs, positions only, same data for all satellites, and a no-op `Appendable` sink to exclude retention of the generated text. Three warmup runs and seven timed runs; median shown. Comparisons were executed in **separate JVM runs** and are noisy, not an established regression threshold.

| Case | Unmodified Writer | Initial B (count only) | Refined B (count + formatted-time check) |
|---|---:|---:|---:|
| 1,000 epochs / 2 satellites / 2,000 coordinates | 16.555 ms | 20.939 ms | 20.530 ms |
| 2,000 epochs / 20 satellites / 40,000 coordinates | 70.102 ms | 62.224 ms | 80.131 ms |

The 20-satellite numbers show measurement noise; B adds scheduling work by construction. Do not infer a stable overhead percentage from these independent runs. The benchmark program is `SP3WriterPerformanceResearchTest.java`. The synthetic sink discards content but real Writer formatting still executes.

## Why this was not previously noticed — evidence and limits

`SP3.validate` counts distinct exact timestamps, while `SP3Writer` uses a grouping tolerance of 0.1% of nominal epoch interval; each convention individually looks reasonable. The inconsistency requires at least two different satellite date streams close enough to collapse. Existing writer regression tests primarily exercise standard SP3 files and ordinary round-trips. Searches of the Orekit GitLab SP3-related issues and MRs found other parser/writer problems but **no exact duplicate in the inspected results**. This supports a missing edge-case regression test, not a claim about how often production systems experience it or when any maintainer first became aware.

## Before any upstream MR

- Decide whether B's extra Writer scheduling overhead is acceptable; assess with realistic high-volume SP3 exports if necessary.
- Move the concise regression tests into existing `SP3WriterTest` and supply the project-standard license header. Keep research-only diagnostics outside the upstream patch.
- Recheck that output formatting extraction preserves golden files; 108 tests already passed.
- Orekit contributing instructions recommend the latest release branch for no-API-change fixes. GitLab personal Fork staging currently uses `develop`, and remains untouched.
- No upstream issue, MR, comment, or push without explicit approval.

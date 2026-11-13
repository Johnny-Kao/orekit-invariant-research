# SP3 epoch collision — Research Refinement

## Status and source

Research-only patch and test fixtures. No Orekit upstream issue, branch, or merge request has been modified.

- Orekit mirror pinned to `90d9b4bdda78891e451d881afa3c11ac9645ac42`
- Hipparchus pinned to `a32301f096e97f098dedbdb3fab41d8343128595`
- Local source archive: `orekit-develop.zip`, SHA-256 `bc910918e42557423f2180697bb81656391fabefa9c40029d8811c69ecc6ff90`
- The original ZIP and the pinned mirror were byte-identical for `SP3.java`, `SP3Writer.java` and `OrekitMessages.java`; this does not establish whole-archive equivalence.

## Original issue — independently reproduced

With a 900 s SP3 epoch interval and two satellites at distinct dates 0.5 s apart, an unmodified `SP3` object passes `validate(false)`. The unmodified `SP3Writer` groups the coordinates into one epoch using `0.001 × epochInterval` tolerance while the header declares two. Re-parsing fails: `found 1 epochs in file research, expected 2`. An ordinary 900 s-separated control round-trips.

## Refined fix

The first prototype separately sorted the dates in `SP3Writer`; the second prototype rejected every pair of adjacent distinct dates closer than the grouping tolerance. **That second prototype was over-strict.** A satellite with consecutive coordinates at 0 s and 0.5 s can still be serialized into two epochs because the writer consumes only one coordinate per satellite per epoch.

The current research patch reuses the existing `NavigableSet<AbsoluteDate>` collected by `SP3.validate()`. Only for outgoing validation (`parsing == false`), it first scans the sorted dates to detect whether any adjacent distinct epochs are within `0.001 × epochInterval`. If none are close, it performs no second scan. If close epochs exist, it checks each satellite's consecutive coordinates against the preceding global epoch. It rejects a near-future coordinate only when that satellite has no coordinate at the preceding global epoch, which is when the current writer would consume and assign the coordinate to the wrong epoch. The original writer logic itself remains untouched.

This is a conservative writer-specific guard for chronologically ordered satellite coordinates. It does not solve arbitrary unsorted input or make wider promises about SP3 timestamp quantization. Any rejected input fails **before writing any output**. Parsed-file validation (`parsing == true`) remains unchanged.

A dedicated `SP3_EPOCHS_TOO_CLOSE` message reports the conflicting dates, the file and the grouping tolerance. All 11 language resources have the key; English and French have populated text, other languages follow the existing empty-translation fallback convention. `OrekitMessagesTest` count was updated from 375 to 376.

## Verification

Local Java 21, Orekit 14.0-SNAPSHOT, Hipparchus 4.1-SNAPSHOT:

```
mvn -B -ntp '-Dtest=SP3*Test,OrekitMessagesTest' -DfailIfNoTests=true test
mvn -B -ntp -DskipTests checkstyle:check
```

- **105/105 PASS**, 0 failed/errors; Checkstyle: **0 violations**.
- 0.5 s cross-satellite collision: rejected before writing.
- 1 ns cross-satellite collision: rejected before writing.
- 0.5 s same-satellite consecutive samples: accepted and round-trips with two epochs; this regression failed against the earlier over-strict preflight.
- Identical timestamps across satellites: one epoch round-trips.
- Ordinary 900 s spacing and 900.5 s allowed jitter: round-trip.
- Six OrekitMessages localization and consistency checks: PASS.

## Incremental performance

This benchmark measures the difference between fully instantiated `SP3.validate(true)` and `SP3.validate(false)` on *normal, non-colliding* data with two satellites. Four warmup pairs and ten alternating timed pairs; medians. The comparison has normal JIT and timing noise and should not be interpreted as a stable throughput claim.

| Environment | Epochs | Coordinates | Parsing validation median | Outgoing validation median | Difference |
| --- | ---: | ---: | ---: | ---: | ---: |
| macOS Java 21 (refined) | 10,000 | 20,000 | 3.529 ms | 3.845 ms | +0.316 ms |
| macOS Java 21 (refined) | 100,000 | 200,000 | 20.820 ms | 26.918 ms | +6.097 ms |

The non-collision fast path adds `O(E)` time and `O(1)` auxiliary memory on top of the pre-existing epoch set. For close-epoch data, the additional scan of satellite coordinates involves `NavigableSet.lower()`, which can cost up to `O(C log E)` time; no separate set of epoch dates is allocated. E denotes distinct epochs and C denotes coordinates. The rare collision-path overhead is not benchmarked here.

## Repeatability and upstream boundary

`.github/workflows/orekit-sp3-refinement.yml` checks out fixed Orekit and Hipparchus source revisions, applies `patches/sp3-epoch-collision.patch`, installs research-only test fixtures and runs the SP3 plus OrekitMessages suites. Only the research repository is updated by this workflow.

Official Orekit collaboration uses its [GitLab forge](https://gitlab.orekit.org/orekit/orekit) and merge requests targeting `develop`, not GitHub pull requests. The research repo remains on GitHub until a separate upstream submission is authorized. The maintainer-facing patch can later be applied to a personal fork of Orekit on GitLab.

## Pre-upstream considerations

- Test additional, less usual SP3 satellite segment ordering if the maintainer wants that supported.
- Review the effect of stricter outgoing validation on other callers of `SP3.validate(false)`.
- Check whether the maintainers prefer a shorter guard without a new translated message or an alternative place for validation.
- No upstream write without explicit approval.

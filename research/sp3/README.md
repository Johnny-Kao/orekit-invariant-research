# SP3 epoch collision — Research Refinement

## Status

Research-only patch and test fixtures. No Orekit upstream issue, branch, or merge request has been modified.

Pinned Orekit mirror commit: `90d9b4bdda78891e451d881afa3c11ac9645ac42`

Pinned Hipparchus commit: `a32301f096e97f098dedbdb3fab41d8343128595`
Local archive: `orekit-develop.zip`, SHA-256 `bc910918e42557423f2180697bb81656391fabefa9c40029d8811c69ecc6ff90`.

SHA-256 of the original ZIP contents and the pinned Orekit mirror were compared for `SP3.java`, `SP3Writer.java`, and `OrekitMessages.java`: all three were byte-identical. This does **not** establish whole-archive equivalence.

## Reproduction on unmodified Orekit

Construct two satellites with distinct coordinate dates 0.5 s apart, with a 900 s epoch interval, and a header declaring two epochs. The source passes `SP3.validate(false, ...)`. The unmodified `SP3Writer.write()` outputs one epoch because of its `0.001 × epochInterval` grouping tolerance; `SP3Parser` rejects the output: `found 1 epochs in file research, expected 2`.

Control at an exact 900 s separation: two epochs output; parse succeeds.

## Refinement

The first research-only prototype checked a separate `TreeSet` inside `SP3Writer`, adding an unnecessary sort and memory allocation. The refined patch instead reuses the `SortedSet<AbsoluteDate>` that `SP3.validate()` already constructs. The check is enabled only when `parsing == false`; lenient parsing validation is unchanged.

Distinct adjacent sorted epochs with a gap `<= 0.001 × epochInterval` are rejected **before the writer emits any output**, using a dedicated `SP3_EPOCHS_TOO_CLOSE` error that identifies both conflicting times, file name and tolerance. The existing time grouping logic of `SP3Writer` is not changed.

This is a strict preflight behavior change for previously accepted near-collision inputs. It avoids silent output corruption; callers seeking such inputs must use an appropriate time representation or normalize their data. The check deliberately does not attempt to silently change the epoch count.

## Validation (local OpenJDK 21, Orekit 14.0-SNAPSHOT)

`mvn -B -ntp '-Dtest=SP3*Test' -DfailIfNoTests=true test`:

- 58 SP3Parser tests passed
- 32 SP3Writer tests passed
- 2 SP3CoordinateHermiteInterpolator tests passed
- 5 collision/control research tests passed
- **97/97 passed; zero failures/errors**

The four distinct behaviors exercised by the research tests: a 0.5 s collision, a 1 ns collision, identical timestamps on separate satellites, and clean round trips with 900 s and 900.5 s separations. For collision inputs, the writer's StringWriter remains empty.

## Benchmark

Method: synthetic but fully instantiated `SP3` objects with two satellites, same epoch grid, and `validate(true)` vs `validate(false)`; four warmup pairs, ten alternating-order timing pairs, medians reported. The two methods traverse the same underlying objects and the only additional check in `validate(false)` is the linear sorted-set scan (subject also to existing minor validation differences). No dedicated extra sort or second `TreeSet` is created. Local timings are approximate; they are not portable performance guarantees.

| Epochs | Coordinates | `validate(true)` median | `validate(false)` median | Difference |
| ---: | ---: | ---: | ---: | ---: |
| 10,000 | 20,000 | 3.429 ms | 3.471 ms | +0.042 ms |
| 100,000 | 200,000 | 21.478 ms | 26.093 ms | +4.614 ms |

Asymptotic **incremental** cost: `O(E)` time and `O(1)` auxiliary space, after the existing `O(E log E)` ordered-set construction. E is the number of distinct epochs.

The benchmark test is diagnostic only and has no performance assertion.

## Reproduce in CI

`.github/workflows/orekit-sp3-refinement.yml` checks out pinned sources, installs Hipparchus, applies `patches/sp3-epoch-collision.patch`, copies the two research tests and runs `-Dtest=SP3*Test`. This is an independent research workflow, not an upstream change.

## Remaining before upstream proposal

- Check any additional `SP3.validate(false)` callers against this stricter rejection contract, beyond the SP3 suite.
- Check potential edge cases where records are near each other **within the same satellite**, which may already generate two writer epochs rather than coalescing. Consider whether the preflight should precisely mirror writer grouping rather than conservatively reject all close distinct global epochs.
- Confirm repository localization conventions for the newly introduced `OrekitMessages` key.
- Do not submit upstream without separate approval.

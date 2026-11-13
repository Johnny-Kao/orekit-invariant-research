# Alternative B — SP3Writer-only preflight (research)

## Status

Independent A/B variant. The current GitLab personal fork Staging branch is **not changed**. This research-only branch patch targets pinned Orekit commit `90d9b4bdda78891e451d881afa3c11ac9645ac42`.

## What changes

After the pre-existing `sp3.validate(false, outputName)` check but **before** the writer emits its header, simulate the existing Writer epoch-consumption loop using new `CoordinatesIterator` objects. If the number of output epochs differs from the header, throw the already existing `SP3_NUMBER_OF_EPOCH_MISMATCH` exception. Normal writing uses its original iterators and is unmodified.

This is an exact check of the count that Writer's current grouping logic would output, not a new rule deciding which epochs *ought* to match. Input `SP3.validate(false)` retains its old behavior. No new `OrekitMessages` key, localization file, or public API change is required.

Files touched in the production patch: `SP3Writer.java` only; **+24 lines, 0 removed**. Research regression test is separate.

## Confirmed tests (macOS, Java 21)

`mvn -B -ntp '-Dtest=SP3*Test,OrekitMessagesTest' -DfailIfNoTests=true test` returned **105/105 PASS**. `mvn -B -ntp -DskipTests checkstyle:check` returned **0 violations**.

Test cases:
- 0.5 s offset across two satellites: Writer rejects before writing any output, while `SP3.validate(false)` continues accepting the input.
- 1 ns offset across two satellites: similarly rejected.
- Same satellite has consecutive 0.5 s epochs: Writer accepts and round-trips.
- Same exact epoch across satellites: accepted.
- Ordinary 900 s gap, and 900.5 s shifted epoch: accepted and round-trip.
- Existing SP3Writer, SP3Parser, and localized-message tests pass.

## A/B assessment

| Dimension | A: stricter public validate | B: Writer-only preflight |
|---|---|---|
| Changed production code | `SP3.java`, `OrekitMessages.java`, 11 localization files | `SP3Writer.java` only |
| Source insertion | +72/-1 plus count test and 154-line new regression test | +24/-0 plus 154-line research regression test |
| Existing public validate(false) behavior | Restricts inputs | Preserved |
| Failure before output | Yes | Yes |
| Existing writer epoch-selection semantics | Preserved | Preserved |
| Error string | New specific `SP3_EPOCHS_TOO_CLOSE` | Existing `SP3_NUMBER_OF_EPOCH_MISMATCH` |
| Incremental work on ordinary data | O(E) extra scan | One complete scheduling/iterator pass, roughly O(E*S + C) |
| Additional memory | O(1) extra | O(S) extra iterators |
| Tests | 105 pass on relevant suites (incl. benchmark) | 105 pass on relevant suites (incl. benchmark) |
| Maintenance | More new logic, translation overhead | Shorter and Writer-local; duplicated epoch grouping expression |

Here E is emitted epoch count, S number of satellites, C number of coordinates. The Writer itself already scans iterators for each epoch. Therefore B doubles **scheduling**, not the whole output formatting cost. A can be faster on large normal inputs; B is simpler and preserves the public validation contract. A's near-collision path can also incur `O(C log E)` checks.

## Why this escaped existing tests (hypothesis, not historical proof)

SP3Writer's grouping tolerance is a legitimate response to small satellite timestamp discrepancies, while `SP3.validate` checks the count of **exactly distinct** epoch dates. These independently reasonable conventions become inconsistent only if different satellites supply near-but-not-equal timestamps. The existing SP3Writer test cases focus heavily on standard compliant files and round-trips; they do not establish coverage of this specific near-collision cross-satellite arrangement.

Searches of Orekit GitLab SP3 issues/MRs found many other SP3 parser/writer reports, but no exact duplicate for this cross-satellite epoch-count collapse among the results inspected. This is not proof that no one ever noticed or that production users encounter it often.

## Remaining engineering work before a maintainer proposal

- Compare the runtime cost of B's extra schedule scan against A in a large multi-satellite *realistic writer workload*, not merely the existing `SP3.validate` benchmark.
- Replace research-only test class/header/prints with idiomatic tests in `SP3WriterTest`.
- Consider extracting the shared match predicate so the preflight and actual writer cannot drift if grouping semantics evolve.
- Check target branch: Orekit contributing guide prefers the latest release branch for bug fixes without API changes; current GitLab staging is on `develop`. Do not rebase or push without further approval.

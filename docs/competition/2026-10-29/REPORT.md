# 29 October engineering report

## Outcome

The first reliability change is implemented with recorded red/green test evidence.
The unchanged engine and the fixed candidate are preserved separately, and a
repeatable local UCI timing harness now exists. Full build: **99 tests passed,
zero failures, zero errors, zero skipped**.

This is not yet a tournament-ready release or a demonstrated strength/speed gain.
The remaining work and its TDD acceptance gates are in [TDD-PLAN.md](../TDD-PLAN.md).

## What changed

`src/main/java/com/titan/search/Searcher.java` now:

- Obtains a legal root fallback before the first deadline check.
- Detects root mate/stalemate even when the search budget is already exhausted.
- Keeps depth zero and an empty PV when no iteration completed; the fallback is
  not presented as an analysed move and its score remains unknown (`Score.NONE`).
- Discards interrupted depth-one work just like interrupted deeper iterations.
- Saves an immutable PV snapshot after each completed iteration and returns that
  snapshot if the following iteration is interrupted.

`SearchFallbackTest.java` supplies eight cases: four playable board situations,
root mate, root stalemate, interrupted-PV preservation, and a successful subsequent
mate search. The playable expiry cases also verify FEN and Zobrist-key restoration.

`.gitignore` excludes local binary/source archives under `artifacts/` and explicitly
retains the three TDD/build evidence logs under this report directory.

`testing/measure_uci.py` measures actual external-process UCI response time with
bounded watchdogs, separate first/subsequent searches, a drained stderr pipe,
cleanup of its own child processes, binary hashes, and full UCI transcripts. It
uses the Python standard library and makes no network requests. It refuses to
overwrite an existing result file.

## Test-driven development evidence

| Stage | Command | Outcome |
| --- | --- | --- |
| Original baseline | `mvn package` | 91 tests passed; unchanged JAR archived |
| Red | `mvn -Dtest=SearchFallbackTest test` | 8 cases, 7 failing assertions |
| Green | Same focused command | All 8 cases passed |
| Full regression and package | `mvn package` | All 99 tests passed; candidate archived |
| Whitespace check | `git diff --check` | Passed |

Red failures included four null moves on playable boards, an incorrect root mate
score, a falsely completed depth at root stalemate, and loss of the completed PV
when the next iteration was interrupted. The reused-search case already passed.

During test development an initial assumption that a particular deadline poll
would always interrupt depth five was invalid: the node counter can skip a polling
multiple. That fixture was corrected to observe completed iterations before the
final red run. The retained red log shows the real PV failure, not that bad fixture.

Evidence files: [red log](tdd-red.log), [green log](tdd-green.log),
[full build](full-suite.log). Tests use deterministic injected expiry, not sleeps.
The implementation was intentionally small; no time policy or tactical heuristic
was changed to make these tests pass.

## Preserved artifacts

Original source commit: `b3992bfb9e25389af076fec997248841222566fe`.

| Artifact | Local path | SHA-256 |
| --- | --- | --- |
| Baseline JAR | `artifacts/2026-10-29/baseline/titan-baseline.jar` | `9a02e2908f0926c1cfeec2274ea492792feedb8740de14608a7adefb14bed979` |
| Candidate JAR | `artifacts/2026-10-29/fallback-candidate/titan-fallback.jar` | `0a987b31e9b23e33efea0b6c5f9bc06d80126bf639e7ac273c3de4771e0248d4` |

Each artifact directory also contains a source ZIP and manifest. Report
copies of the manifests list individual source hashes:
[baseline](baseline-manifest.json), [candidate](candidate-manifest.json).
The candidate is an uncommitted working-tree change against the listed base commit;
it is not identified by that commit alone. The ZIP/hashes identify the exact source.
Archives do not include bridge credentials or unrelated reference-engine folders.

## Timing results

The main measurements ran sequentially without concurrent Maven builds, on the
local Windows host using Java 25.0.1, default 64 MB hash, and one engine process at
a time. Three positions: start position, Kiwipete, and a rook endgame. Each mode
has six samples: first and subsequent search for each position. `ucinewgame` clears
the TT, but not all JVM/history state. This is a diagnostic sample, not statistical
evidence of speed or Elo improvement. JDK 17 performance was not measured here.

| Search request | Baseline min / median / max ms | Candidate min / median / max ms |
| --- | --- | --- |
| `go movetime 100` | 53.857 / 56.661 / 97.567 | 54.776 / 65.376 / 103.678 |
| `go movetime 1000` | 514.091 / 624.084 / 780.854 | 620.679 / 677.731 / 1002.905 |
| Full 3+2 clocks (180000 ms + 2000 ms increment) | 4111.608 / 6254.181 / 7171.300 | 3817.281 / 4452.763 / 7785.512 |
| Low clock (1000 ms remaining + 2000 ms increment) | 251.051 / 254.633 / 262.615 | 255.330 / 263.453 / 273.184 |

There were 24 samples per binary, with no harness error, process timeout, or
`bestmove 0000` in these nonterminal positions. This harness checks protocol output;
it does not independently validate move legality or play full games. Legal fallback
behaviour is checked by the Java regression tests.

Startup-to-ready ranged from about 245 to 359 ms for the baseline and 249 to 356 ms
for the candidate. Search wall times above start at `go`, not process launch.

Interpretation:

1. The current clock policy deliberately permits seconds of thought at 3+2: with
   unadjusted clocks it computes a 7.5-second soft and 22.5-second hard limit, and
   only starts another iteration before half the soft limit is used. Local results
   fit deliberate allocation; they do not establish why particular real games lost.
2. Low-clock hard limit is 250 ms here. Observed wall times exceed it by up to
   12.615 ms in the baseline and 23.184 ms in the candidate. These are end-to-end
   local overshoots including process I/O, not precise estimates of polling cost.
3. The candidate's worst sampled 100 ms request took 103.678 ms. Legal fallback
   prevents one failure mode but is not a hard-deadline guarantee.
4. Differences across these small samples are noisy. The lower 3+2 median does
   **not** establish a speedup; some candidate cases took longer. No search-speed
   optimisation has been claimed or accepted on this evidence.

Primary data: [baseline timing](baseline-timing-isolated.json),
[candidate timing](candidate-timing-isolated.json), [summary](timing-summary.json).
`baseline-timing.json` is an earlier exploratory run that overlapped test builds;
it is retained for transparency and excluded from the comparison table.

## Outstanding dependencies and next action

- Review/send [the organiser draft](organiser-clarification.md). No message was
  sent. The PeSTO/AI-assistance eligibility questions remain unresolved.
- Begin the 30 October work with deterministic stop/start lifecycle tests, then
  clock-budget invariants and quiescence check handling. The full procedure is in
  [the TDD plan](../TDD-PLAN.md).
- Existing production bot config is unchanged. Its eventual event configuration
  still needs casual blitz acceptance and disabled automatic matchmaking.
- No external matches, Lichess games, opponent benchmarks, rating estimate, or
  full-game 3+2 flagging diagnosis were performed in this slice. Local timing
  cannot substitute for those. The preserved versions still need actual games.
- The current runnable `target/` JAR was rebuilt with the fix. Do not deploy the
  candidate as the final competition build until the remaining gates pass.

The original untracked reference files and testing results were left intact. No
Git commit, push, account change, registration, or submission was performed.

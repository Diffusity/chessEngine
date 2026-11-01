# Titan: competition preparation with test-driven development

Progress update: the follow-up reliability implementation is described in
[RELIABILITY-UPDATE.md](2026-10-29/RELIABILITY-UPDATE.md). Lifecycle cancellation,
clock-budget invariants, and basic quiescence regressions below are now implemented;
profiling, match validation, eligibility clarification, and release rehearsal remain.
The original dated report is retained as historical evidence.

Working date: 29 October 2026, Asia/Kolkata.
Working submission deadline: 2 November 2026, 12:00 noon, from the event email.
Event: 3 November, 21:00. Objective: maximise the chance of surviving the 3+2
knockout while remaining eligible and reproducible. No test suite or rating
estimate can guarantee a win against unknown opponents.

## 1. Current status and boundaries

The 29 October engineering slice is implemented: preserve the unchanged engine,
measure local UCI timing, reproduce the missing fallback with tests, implement a
small correction, and preserve a candidate for comparison. An organiser question
is drafted, but eligibility is not resolved until they answer or the disputed
parameters are replaced. No message has been sent and no bot configuration or
account has been changed.

| Work item | Status | Evidence |
| --- | --- | --- |
| Freeze original source and binary | Complete | `2026-10-29/baseline-manifest.json`; local archive under `artifacts/2026-10-29/baseline/` |
| Establish existing correctness baseline | Complete | Original `mvn package`: 91 tests passed |
| Reproduce timeout failure before implementation | Complete | `2026-10-29/tdd-red.log`: 8 tests, 7 failures |
| Implement legal root fallback | Complete | `Searcher.java`; `SearchFallbackTest.java` |
| Preserve last completed result/PV | Complete | Deterministically interrupted iteration regression |
| Verify candidate and package it | Complete | `2026-10-29/full-suite.log`: 99 tests passed |
| Local latency measurement harness | Complete | `testing/measure_uci.py`, saved JSON transcripts |
| Confirm rules on PeSTO / AI assistance | Awaiting organiser clarification | Draft in `2026-10-29/organiser-clarification.md` |
| Establish full-game 3+2 strength | Outstanding | Requires games and PGNs, not just position timings |

The original source archive deliberately contains source/build files only, not the
bridge config or any API token. Baseline artifacts are local and ignored by Git.
Preserve/copy them before cleaning the workspace. The baseline still uses disputed
PeSTO parameters: it is a diagnostic reference, not an approved submission.

## 2. The TDD workflow for every next change

1. State one externally observable requirement and its failure mode.
2. Write the smallest deterministic regression test using an independently
   established expected result. Never make a test pass by copying the implementation
   formula into its assertion.
3. Run it against the current implementation. Record an actual assertion failure;
   compilation failures, missing dependencies, or a wrong test fixture are not the
   desired red phase. Fix faulty test assumptions before proceeding.
4. Implement the smallest change that meets the requirement. Keep unrelated search
   heuristics, weights, and deployment settings unchanged in that experiment.
5. Rerun the focused tests, then refactor if useful and rerun affected tests.
6. Run `mvn package` before promoting a candidate. Preserve source, JAR hash,
   test result, benchmark command, settings, and eventual match PGNs.
7. For speed or strength claims, run separate measurements or matches. Passing
   functional tests proves neither increased Elo nor perfect deadline compliance.

Use controllable clocks/cancellation for unit tests. Use generous watchdogs to
prevent process-level tests hanging; measure real deadline overshoot separately.
Do not put brittle millisecond performance thresholds into ordinary CI tests.

## 3. Completed slice: legal fallback and completed-result preservation

Requirement: every search on a playable board has a legal answer even when its
budget expires before the first iteration. Interrupted work must not be reported
as a completed depth or overwrite the last completed principal variation.

The eight regression cases cover:

1. Expiry at the initial position with White to move.
2. Expiry with Black to move.
3. Expiry while Black must evade a rook check.
4. Expiry with a pawn on the seventh rank and promotions available.
5. Root checkmate with an expired budget: no move and the correct terminal score.
6. Root stalemate with an expired budget: no move and a draw score.
7. An interrupted deeper iteration: retain completed depth, score, move, and PV.
8. A subsequent search after timeout still finds a known mate in one.

Tests also check board/FEN/hash restoration in the expired-budget cases. Expiry
is injected through a SearchLimits test subclass, not sleeps. The deeper-iteration
test records how many iterations really completed rather than assuming a fixed
poll always lands at a particular depth.

Implementation: generate root legal moves before entering iterative deepening;
recognise terminal roots; use the first legal move as an emergency fallback;
discard every interrupted iteration including depth one; snapshot completed PVs.
An unsearched fallback has depth zero, an empty PV, and an unknown score sentinel.
It is not advertised as a tactical solution. This adds one root move-generation
pass per search; performance impact must be measured, not assumed absent.

Not fixed in this slice: stop/start race, thread lifecycle, quiescence check handling,
time-allocation policy, polling frequency, evaluation provenance, move-generation
speed, and live challenge settings. Root checkmate handling here is distinct from
the still-open checkmate problem inside recursive quiescence search.

## 4. Next: safe search lifecycle (30 October, first slice)

Red tests:

- `go` immediately followed by `stop` produces exactly one legal bestmove and leaves
  no search worker alive. Use a barrier/latch to deterministically exercise stop
  before the worker begins its search; do not rely on scheduler luck.
- Stop during an active search unwinds all board moves and preserves its result.
- A new position, new game, or hash resize cannot mutate state used by a running
  worker. A replacement search cannot overlap its predecessor.
- Multiple consecutive games in the same process cannot emit a stale bestmove.

Green changes: define a per-search cancellation lifecycle; initialise cancellation
before launch; make stop observable during startup; join the worker before changing
shared state; do not silently proceed when a timed join leaves it alive. Keep stdout
restricted to UCI responses and put diagnostics on stderr.

Gate: tests pass with deterministic scheduling, process-level UCI smoke tests pass,
and an immediately stopped search always returns a legal move on playable boards.

## 5. Next: blitz deadlines and clock allocation (30 October, second slice)

First establish clock semantics: the bridge already subtracts setup/network
overhead from the current player's clock. Its current `move_overhead` is 2000 ms.
Measure actual bridge/network latency before reducing this. Keep network reserve
and engine cancellation reserve explicit so they are not accidentally counted twice.

Red tests for SearchLimits:

- Select White's or Black's clock/increment correctly.
- Low or zero available time cannot produce a budget above usable time.
- An increment that arrives after the move is not spendable before it.
- Soft deadline never exceeds hard deadline, and hard deadline never exceeds usable
  remaining time. Explicit movetime, fixed depth, and infinite search retain their
  documented behaviours.
- A fake monotonic clock crossing the deadline cancels search predictably.

Green: first fix these invariants, then experiment with the allocation constants.
One candidate policy, not a proven optimum, is `usable/40 + 0.6*increment` for the
soft target and `min(2*soft, usable/5)` for the hard budget, with valid nonnegative
clamps and immediate fallback when no usable time remains. Model explicit zero
clock separately from a command that supplied no clock. The existing zero-clock
path can otherwise be interpreted as infinite search.

Test polling every 256 nodes under short budgets versus the existing 2048-node
mask. Be careful: increments from pruned moves can skip exact node multiples;
consider an elapsed-node threshold rather than relying only on equality at a mask.
Cover both main search and quiescence, and measure overhead and worst overshoot.

Gate: on the event machine, no observed flagging or deadline violations beyond a
documented measured reserve in the stress corpus. This is empirical confidence,
not a hard real-time guarantee under arbitrary OS/JVM pauses. Extend testing to
long 3+2 games and capture actual clocks after every move.

## 6. Next: quiescence correctness (30 October, third slice)

Red tests: a checking capture whose reply requires a quiet evasion; checkmate at
a recursive quiescence node; a quiet promotion at the horizon; a forced capture
evasion that must not be removed by SEE pruning. Verify complete board restoration.
Use legal, small positions with hand-verifiable expected outcomes.

Green: disallow stand pat in check, generate all legal evasions, score mate when
none exist, include quiet promotions, and keep deadline/cancellation checks active.
Then fix the LMR history lookup to use the mover's colour. Treat pruning changes
as separate experiments so their effect can be attributed.

Gate: tactical regressions and perft pass; candidate games show no tactical
regression. Do not increase pruning to compensate for slow code before correctness.

## 7. Next: measured efficiency improvements (31 October)

Profile CPU and allocation on the actual event JDK/hardware, under identical
positions, hash size, and search limits. Keep first-search and reused-process
measurements separate. Record both nodes and wall time; NPS alone is insufficient.

Prioritise these separate changes only when profiling supports them:

| Change | Red/characterisation tests before optimisation | Acceptance evidence |
| --- | --- | --- |
| Direct tactical move generation | Compare captures/promotions with filtered legal moves across curated and seeded random legal positions | Same moves; less allocation/time |
| One make/unmake per searched move | Legal-move differential tests and perft; pinned pieces, castling, en passant, double checks, underpromotions | Identical legal trees and terminal scoring |
| Reusable per-ply buffers/undo states | Deep make/unmake restores FEN, hash, pawn hash, castling, en passant, clocks | No aliasing between recursive plies; lower allocation |
| Faster slider attacks | Compare new attacks to current ray scans for all squares and seeded occupancies | Identical attacks; lower measured runtime |
| Evaluation/TT allocation reduction | Evaluation symmetry/known scores, TT collisions/mate distance tests | Same intended semantics; lower CPU/GC cost |

Do not introduce threads, a new language, or an NNUE training pipeline in this
short preparation window. No speed improvement should be called stronger until
it survives game testing at the target time control.

## 8. Eligibility and evaluation decision

Review and send `2026-10-29/organiser-clarification.md`. If published PeSTO values
are not explicitly allowed, independently define material/PST parameters and
document their rationale and origin. Do not merely rename or perturb copied tables.
Audit all core search/evaluation sources and disclose permitted adaptations and
AI assistance according to the organisers' answer.

Write evaluation tests for colour/board symmetry, material signs, game-phase
behaviour, and chosen chess features before replacing the tables. Use human-game
outcomes or data from a compliant own engine if tuning. Keep training/validation
games disjoint. Re-establish the baseline after evaluation changes. Do not use
another engine's scores or weights as labels or targets.

This is an eligibility dependency, not a reason to stop independent correctness
work. Only the organisers can resolve their contradictory guidance.

## 9. Match validation, release, and submission (31 October - 2 November)

1. Preserve each candidate and compare it against the baseline with colours swapped.
   Record engine hashes, TC, concurrency, JVM options, host, command, and PGNs.
2. Use faster matches to reject regressions. Use the existing fastchess setup for
   statistical comparisons with a predeclared stopping rule; incomplete/inconclusive
   runs are not evidence of improvement. Diversify test positions using permitted
   sources; do not deploy an opening book or lookup moves during competition.
3. Validate retained candidates at 3+2. Include games without early adjudication,
   so wins must actually be converted and long games exercise the clock.
4. On 1 November, test casual 3+2 and 10+5 through the real bridge on the event host.
   Configure standard chess, required controls, authorised challenger acceptance,
   and disabled matchmaking. Confirm books/tablebases/online move sources remain off.
5. Rehearse consecutive games and cold startup. Keep the final executable running
   reliably, with tested power/network and without competing heavy workloads.
6. Freeze one version, prepare `engine/`, `source/`, and README with an exact quiet
   launch command, Java requirement, bot name, approach, and source declarations.
   Ensure ignored JAR files actually reach the submission package. Rebuild from a
   fresh extracted source archive. Keep private bot credentials out of the repository.
7. Submit before 2 November noon unless an organiser explicitly changes the deadline.
   The 10+5 stage uses the same submitted version. Do not change/restart/advice-feed
   the engine during games.

Final readiness gate: reproducible build, all required tests passing, no observed
illegal moves/crashes/flagging in the declared rehearsal sample, correct challenge
acceptance, resolved eligibility, preserved artifact hash, and demonstrated 3+2
performance. A passing gate increases confidence; it does not guarantee a win.

## 10. Reproduction commands (PowerShell, from the repository root)

```powershell
mvn -Dtest=SearchFallbackTest test
mvn package
python testing/measure_uci.py --jar artifacts/2026-10-29/baseline/titan-baseline.jar --output artifacts/baseline-rerun.json
python testing/measure_uci.py --jar artifacts/2026-10-29/fallback-candidate/titan-fallback.jar --output artifacts/candidate-rerun.json
```

Python 3.10+ is sufficient for the standard-library measurement harness. In this
session the bundled Python executable was used; Maven used its configured JDK and
the timing harness invoked `java` from PATH. Runtime details are recorded in the
JSON outputs. Use new output filenames: the harness refuses to overwrite evidence.
Run measurements sequentially without concurrent builds. These commands do not
connect to Lichess, send messages, or launch external opponents.

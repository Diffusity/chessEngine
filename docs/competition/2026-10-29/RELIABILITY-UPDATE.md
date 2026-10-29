# Search reliability implementation

Branch: `codex/blitz-search-reliability`. This update follows the first fallback
slice described in REPORT.md. It implements the next correctness/time-management
stages of TDD-PLAN.md; it does not claim completion of the entire competition plan.

## Behaviour changes

- An immediately cancelled search retains cancellation even if the worker has not
  entered Searcher.search yet. Engine.stopSearch interrupts and joins its worker.
- Position changes, move application, new games, hash resizing, and replacement
  searches wait for the previous worker. If it cannot stop within two seconds,
  an explicit failure prevents further mutation rather than letting workers overlap.
- The existing legal fallback and last-completed-PV preservation are retained.
- Missing clocks are represented separately from an explicit zero. Zero clock or
  zero movetime returns the fallback instead of entering unlimited analysis.
- Soft/hard budgets are nonnegative, ordered, and bounded by current available time.
  Future increments affect the target but cannot expand spendable remaining time.
- A 10 ms local reserve is separate from the bridge's existing network overhead.
  The candidate policy uses 40 estimated moves remaining, 60% of increment, a hard
  cap of the smaller of twice the soft budget and one fifth of usable clock, and
  the existing half-soft-budget rule for starting another iteration. These are
  experimental constants, not tuned or statistically validated playing-strength gains.
- Deadline polling uses a moving 256-node threshold instead of exact 2048-node
  multiples, which could be skipped by pruning. Thread cancellation is checked at
  every search entry. OS/JVM pauses can still cause wall-clock overshoot.
- Quiescence handles all check evasions without stand pat/SEE pruning in check,
  detects terminal positions, and includes quiet promotions. It currently generates
  full legal moves before filtering outside check; profiling/optimisation is deferred.
- The LMR history lookup now uses the colour of the mover rather than the opponent.

## TDD and validation

| Stage | Evidence |
| --- | --- |
| Lifecycle red | 6 tests, 6 assertion failures (`lifecycle-red.log`) |
| Clock/quiescence red | 17 tests, 14 assertion failures (`search-red.log`) |
| Focused green, including prior fallback tests | 31 tests passed (`reliability-green.log`) |
| Additional clock boundary/combined-budget cases plus full build | 124 tests passed, zero failures/errors/skips (`reliability-full.log`) |
| Packaged executable UCI checks | 25 passed (`reliability-uci-checks.json`) |

The worker-startup tests use a latch and an injectable ThreadFactory. The
pre-cancelled search test uses a dedicated interrupted worker. Clock tests include
an injectable monotonic clock with no sleeping. Process checks run 20 consecutive
go-infinite/stop cycles, exhausted/very-low budgets, and root mate/stalemate in the
same JVM, check legal starting moves, and reject duplicate bestmove responses.

Run locally:

```powershell
mvn package
python testing/check_uci_reliability.py --jar target/titan-chess-engine-1.0.0-SNAPSHOT.jar --output artifacts/uci-rerun.json
python testing/measure_uci.py --jar target/titan-chess-engine-1.0.0-SNAPSHOT.jar --output artifacts/timing-rerun.json
```

The scripts require Python 3.10+ and only the standard library. Choose fresh output
paths. The session used the bundled Python runtime and Java 25.0.1 for process checks;
CI builds/tests with Java 17. Search source targets Java 17.

## Timing interpretation

`reliability-timing.json` records another 24 local searches, sequentially without
parallel build work. `reliability-summary.json` records the binary hash and aggregates.
The new time allocation is intentionally more conservative than the preserved
baseline. Shorter thinking times are not evidence of more efficient computation or
better move choices. The 100 ms diagnostic still exhibited overshoot, so this is
not a hard real-time guarantee.

No full 3+2 games, network latency measurements, statistical strength comparison,
or external opponent games were run. Keep the old executables as diagnostic
baselines. Test this candidate in actual games before promoting it for competition.

## Remaining work

1. Resolve organiser questions about published evaluation parameters and AI assistance.
2. Profile move-generation/allocation costs and optimise one measured bottleneck
   at a time with differential/perft tests, especially quiescence move generation.
3. Run colour-swapped candidate/baseline matches, then complete 3+2 games without
   early adjudication to measure flags, conversion, and actual clock consumption.
4. Configure and rehearse casual 3+2/10+5 acceptance on the event machine, freeze the
   approved executable, and prepare the required submission package.

Bridge credentials/config, account settings, reference engines, and old PGNs are
not part of this change. No online challenge or organiser message was sent. The
remote license-only commit was incorporated before preparing the PR.

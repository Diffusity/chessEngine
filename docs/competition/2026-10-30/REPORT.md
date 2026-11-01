# 30 October: measured search optimisation and match validation

The tactical move-generation optimisation is implemented and locally tested.
The full 3+2 strength checks are still running. Do not describe this candidate as
competition-approved or stronger on the basis of these timing measurements.

## What changed and why

The frozen reliability candidate's JFR workload recorded 3,716 execution samples.
Legal move generation appeared in 2,560 stacks (69%); quiescence appeared in 2,693
(72%). These are overlapping inclusive counts, not additive CPU percentages.
BoardState was the largest sampled allocation class. Quiescence was generating,
making and unmaking quiet moves solely to discard them afterwards.

`generateTacticalMoves` now generates legal captures and all promotions directly.
Non-pawn destinations are masked to enemy occupancy without changing sliding-ray
occupancy. Pawn promotions and en passant remain available. Quiescence still
generates every legal evasion when in check. When the tactical list is empty,
`hasLegalMove` stops at the first legal quiet move to distinguish stalemate.
Move order, evaluation, time allocation and pruning thresholds are unchanged.

## Test-driven evidence

1. Added a reference implementation using full legal generation and filtering.
2. Added differential tests over nine special positions and up to 1,200 seeded
   legal positions. They compare ordered moves and verify board/FEN/key restoration.
3. Recorded two expected failures: tactical generation made 20 unnecessary quiet
   moves at startpos; the existence probe made 20 rather than stopping after one.
   See `tactical-red.txt` (four tests, two assertion failures).
4. Implemented direct tactical generation and early exit. All 128 Java tests pass,
   including perft, mate, stalemate, quiet check evasion and quiet promotion tests.
5. All 25 process-level UCI checks pass. Four match/rehearsal configuration tests
   pass. The configuration checks are supplementary, not the red-green evidence.

## Fixed-depth timing

Java 25.0.1, 512 MB heap, depth 8, three warm-up rounds at depth 6, five measured
rounds, TT cleared per search. Search history persists in the reused worker, so
compare corresponding rows rather than expecting every round to have equal nodes.
The workload uses startpos, Kiwipete and a rook endgame. JFR was disabled for timing.

| Position | Before / after median, run 1 | Speedup | Before / after median, reversed order | Speedup |
| --- | ---: | ---: | ---: | ---: |
| Startpos | 111 / 65 ms | 1.71x | 107 / 64 ms | 1.67x |
| Kiwipete | 836 / 436 ms | 1.92x | 975 / 409 ms | 2.38x |
| Rook endgame | 21 / 16 ms | 1.31x | 24 / 14 ms | 1.71x |

All 30 corresponding search rows have identical depth, nodes, score and best move.
These are short local microbenchmarks, with JVM and host variability. They show a
speed improvement on this workload, not an Elo estimate or a guarantee of wins.
Raw CSVs and `fixed-depth-comparison.json` are retained beside this report.

## Real 3+2 games: running, not complete

The initial run stopped during game 6 when its execution session ended. Five
completed games are preserved: candidate 0 wins, 2 losses, 3 draws. All recorded
moves passed independent python-chess legality checks; all five terminations were
normal, with no reported forfeits. This is a diagnostic result, not evidence that
the reliability changes improved playing strength. `baseline-partial-5-games.json`
is explicitly a historical snapshot.

The saved fastchess configuration was resumed from five games, as confirmed in
`console-resumed.log`. The original state and logs were preserved. The validation
driver will complete these 20 games, then run 20 more comparing tactical versus
reliability builds. Both runs use 180+2, concurrency 1, alternating colours, 64 MB
hash, zero runner time margin, and no early resign/draw/maxmoves/tablebase
adjudication. All games start from the standard position; repeated openings mean
the results are correlated and unsuitable for a confident Elo claim.

Live files (ignored local artifacts):

- `artifacts/matches/2026-10-30-validation-status.json`: phase, completion or error.
- `artifacts/matches/2026-10-30-baseline-3plus2-run2/`: original/resumed logs, PGN,
  manifest, and summary refreshed at completion.
- `artifacts/matches/2026-10-30-tactical-3plus2/`: created when the follow-up begins.

Keep the laptop awake and avoid CPU-heavy work until the matches finish. The
runner is a hidden background process, not a recurring automation. It cannot
survive a shutdown. Its status file is updated at phase boundaries; check log
timestamps and process state if progress stops. Do not start a duplicate runner.

## External work still blocked

The existing token authenticated the Lichess BOT account `dumbEngine`. Private
configuration preparation supports exact casual 3+2 or 10+5, one allowlisted
opponent, disabled matchmaking, books, tablebases and online move sources.
The existing bridge config has not been replaced and the bridge has not been
started. An agreed opponent username is still required before live challenges.

The organiser clarification draft in the 29 October directory remains unsent.
No Gmail connector is available, and both attempted browser-control entry points
failed to initialise. A connected mail account is required to send it here.
PeSTO parameter eligibility, permitted AI assistance and submission details remain
unresolved. Do not treat absence of an organiser reply as approval.

## Acceptance before selecting the tournament executable

- Finish both real-time batches and inspect every abnormal termination or clock
  overrun; do not accept a candidate based on NPS alone.
- Compare W/D/L and representative losses. A 20-game startpos batch is a smoke
  test; broaden paired testing before claiming strength.
- Complete the two live casual bridge rehearsals and capture network/clock logs.
- Resolve organiser eligibility and record source declarations before submission.
- Preserve candidate hashes and source archives. Keep PR #1 open until the
  remaining checks support accepting this build.

# Guarded validation and loss review

## Completed engineering work

Windows Kernel-Power events now provide evidence of Modern Standby and sleep
during the problematic periods, including a critical-battery trigger near 21:59.
Modern Standby entry at 15:36 and exit around 15:54 align with the first tactical
overrun; subsequent long gaps also coincide with power transitions. This supports
host interruption as a major confounder, rather than treating the forfeits as
clean playing-strength results. It does not prove every engine deadline path is
correct. Raw power events and prior PGNs remain preserved locally; the PR includes only relevant event IDs and timestamps.

The runner now requests temporary idle-sleep prevention only while a match is
running, requires AC power, and records wall/monotonic clock samples every second.
Disconnecting AC, a sample gap over five seconds, or clock disagreement over two
seconds invalidates the run and stops its owned process tree. The queue stops for
investigation instead of silently continuing. No global power policy is changed.
An explicit sleep request, lid closure or critical battery can still interrupt
Windows; keep the machine open and plugged in. This is deliberately conservative:
a sufficiently long scheduler stall also invalidates the timing experiment.

The guard's failing tests are retained in `guard-red.txt`; ten guard/config tests
pass after implementation. Guard failures are evidence about run validity, not
automatic proof that the chess engine malfunctioned.

## Loss-position comparison

`loss-review.json` compares three frozen builds using full game history on three
positions selected from normally terminated losses. Selection uses the first
logged one-pawn deterioration between candidate turns. This is a diagnostic lead,
not proof of the first objectively bad move. A fourth selected game had no such
qualifying drop. Each sample uses a fresh process, depth-5 warmup, and then either
depth 10 or a requested 1,000 ms. The final recorded run was sequential without
Maven or matches competing for CPU.

At depth 10 the reliability and tactical builds have identical moves, scores and
node counts on all three positions. At a fixed requested time, the completed
depth and sometimes selected move differ. For example, at baseline game 1 ply 25,
depth 10 selects `b5c7` for all builds, while the two newer builds select `a1d1`
at 1,000 ms. At tactical game 18 ply 126, all builds select `h7a7`, while deeper
scores deteriorate. These are horizon/search leads; own-engine scores are not an
independent oracle. We have not proved which move first caused any of these losses.

Do not compensate by adding arbitrary pruning or evaluation parameters. The next
diagnostic step after the direct match is deeper, bounded analysis of both the
played move and alternatives, and one-change ablations of time allocation/search
behaviour if the regression persists. A test asserting an unverified preferred
move would encode a guess, so no such test was added.

## One demonstrated engine defect fixed with TDD

Checkmate at the fifty-move boundary was returned as a draw in both quiescence
and non-root alpha-beta nodes. Two tests failed before the correction: direct
horizon checkmate with halfmove clock 100, and a quiet mate from halfmove clock 99.
The correction checks for checkmate on the existing draw path, avoiding an extra
legal-move scan on ordinary nodes. This defect is independently demonstrated;
it has not been established as the cause of the match losses.

Validation: 130 Java tests pass, 25 packaged UCI process checks pass, and all 15
corresponding fixed-depth rows retain identical nodes/scores/moves compared with
the frozen tactical build. Aggregate times were 2,447 versus 2,517 ms in this
short run; no additional speed claim is made. See red/green evidence, CSVs and
`terminal-comparison.json`. The new executable hash is in `candidate.json`.

## Queued real-time matches — not yet complete

`testing/run_followup.py` runs these sequentially with the guarded runner:

1. Two replacement games: frozen reliability versus original baseline, replacing
   the affected colour pair corresponding to original games 19–20.
2. Six replacement games: frozen tactical versus reliability, replacing the three
   affected pairs corresponding to games 11–12, 13–14 and 19–20.
3. Twenty direct games: corrected terminal candidate versus original baseline.
   Ten locally specified opening positions are played with colours swapped.

All use 3+2, concurrency one, hash 64 MB, zero runner time margin and no early
adjudication. Replacement games use standard startpos. The ten broader positions
are generated from legal short move sequences for testing only; they are not
bundled into the engine or used by the live bridge as a book. They are not an
independent balanced-strength benchmark. A small batch cannot guarantee wins.

Live status: `artifacts/matches/2026-10-30-guarded-followup/status.json`.
Each subdirectory contains manifests, binaries, PGNs, clock samples and logs.
Original results are never overwritten. Only combine replacement results with
the unaffected complete pairs if the replacement run is valid.

## External steps

The user's request to reuse the prior opponent was resolved from the latest local
bridge logs: SuperChessBot1900. The authenticated participant is dumbEngine, with
no active game at the check. The opponent status response did not report it online.
Private exact-control casual 3+2 and 10+5 configs are prepared under ignored
`artifacts/rehearsal/`, using the corrected frozen JAR, one opponent allowlist,
disabled matchmaking and disabled books/tablebases/online move sources. No new
live challenges have been sent. Live rehearsals remain to be completed when the
opponent is available, without overlapping local timing matches.

Gmail is still not connected. The organiser clarification draft remains unsent;
PeSTO and AI-assistance eligibility remain unresolved. Connect Gmail to complete
that step here, or send the existing draft and provide the response. No eligibility
assumption has been substituted for the organiser's decision.

PR #1 remains unmerged pending match and external validation.

# Loss diagnosis and opt-in time-policy experiment

## Findings from three decisive direct-match losses

The loss selector found qualifying one-pawn evaluation drops in games 6, 11 and
14 of the guarded corrected-candidate/original match. These are leads, not proof
of the first objectively bad move. Unlike the earlier startpos-only analysis,
these probes restore each game's initial EPD FEN and complete move history.

The original and corrected engines were compared at depth 14 (15-second ceiling,
all six probes reached depth 14) and equal requested 3,000 ms. The corrected
engine also received a 10,000-ms root probe. Each distinct suggested move and
the played move were then forced and evaluated with an equal 5,000-ms child
budget. Extra legal alternatives were inspected in games 11 and 14. Scores below
are from the root player's perspective, in centipawns, using our own corrected
engine; they are not independent ground truth or exact minimax values.

| Game / ply | Root-search observation | Forced-move comparison |
| --- | --- | --- |
| 6 / 80 | Original depth 14 chooses `g2f1`; corrected chooses played `h3h2`. Longer corrected search changes to `g2f2`. | `g2f1`: 0; `g2f2`: -89; `h3h2`: -152. A useful endgame horizon/search-consistency lead. |
| 11 / 37 | Both depth-14 searches choose played `e3d4`; longer corrected search retains it. | `e3d4`: -76; `f3d4`: -344; `c3b5`: -608. Tested alternatives do not support blaming this particular move. |
| 14 / 64 | Both builds choose played `e6d6`; longer corrected search retains it. | `e6d6`: -424; `c7d6`: -215. Pawn recapture deserves further investigation, although both estimates are losing. |

Full transcripts, actual depths, timings, initial FENs and histories are in
`deep-loss-review.json`. Different search boundaries, histories, pruning and
horizons can produce different estimates. These observations do not establish a
specific new correctness defect. No regression test asserting one of these moves
as objectively best was invented, and no new pruning/evaluation change was made.

## Controlled time-allocation alternative

The existing default starts another iteration only before half the soft budget.
The opt-in experiment starts it before the full soft budget for UCI clock-based
searches. Hard limits, soft-budget calculation, reserves, clock polling, search
and evaluation are identical. Explicit `movetime`, fixed depth and infinite
searches retain their behaviour. The experiment is enabled only by:

`-Dtitan.experimental.fullSoftClock=true`

The public default remains unchanged. This is a hypothesis under test, not a
claimed fix or an approved tournament setting. Control and treatment in the
first match use the identical JAR; only this JVM property differs.

The feature contract was developed with failing tests: the full-budget gate was
initially absent and two tests failed. Tests cover both colours, unchanged fixed
movetime, identical hard deadlines/reserves and exhausted clocks. These enforce
the specified experiment and safety invariants; they do not assert that the
parameter choice is stronger. All 134 Java tests pass, as do 25 UCI process checks
for each policy and 11 runner/configuration tests. All 15 fixed-depth diagnostic
rows have identical nodes, scores and best moves between the two modes.

At clocks reconstructed immediately before the three recorded moves:

| Game | Default time / depth | Experiment time / depth | Observation |
| --- | --- | --- | --- |
| 6 | 2.311 s / 14 | 5.318 s / 16 | Changes `h3h2` to `g2f2`; forced-move comparison still prefers another candidate. |
| 11 | 2.721 s / 12 | 7.161 s / 14 | Same move, two more completed plies. |
| 14 | 3.106 s / 18 | 8.744 s / 18 | Extra iteration fails to complete before hard stop; retained move/depth unchanged. |

These probes show both possible benefit and wasted time. Match evidence must
decide whether the tradeoff helps; the policy is not promoted from these probes.

## Guarded matches launched, results pending

`testing/run_time_policy.py` runs two predeclared sequential batches:

1. Twenty experimental-versus-default games with the identical candidate JAR.
2. Twenty experimental-versus-original-baseline games.

Both use the same ten local opening positions, swapped colours, 3+2, 64 MB hash,
concurrency one and no early adjudication. AC and clock-gap guards remain active.
Inherited JVM-option environment variables are rejected to avoid contaminating
the control. No score-based early stopping or automatic policy promotion is used.
Abnormal termination or guard failure stops the queue for investigation.

Live status and evidence are under `artifacts/matches/2026-10-31-time-policy/`.
The first match is the timing-policy ablation. The second compares the full
candidate against the original and must not be interpreted as isolating timing
alone. Twenty games each remain a diagnostic sample, not a reliable Elo estimate.
Keep the laptop connected to AC and avoid explicit sleep or lid closure.

## External work and merge status

Gmail remains unconnected. The browser fallback was retried and again failed to
initialise with a missing-kernel-assets error; the organiser email is still
unsent. PeSTO and AI-assistance eligibility remain unresolved.

dumbEngine was idle at the new account check; SuperChessBot1900 was not reported
online. The prepared casual rehearsal configs still point to the validated
corrected default build. No challenge or bridge was launched, and no experimental
policy was silently enabled in the live configuration. Rehearsals remain pending
opponent availability and completion of CPU-sensitive local tests.

PR #1 remains unmerged. After the paired results, evaluate W/D/L together with
clock usage, completed depths, abnormal endings and representative losses. If the
timing alternative does not help, leave the default untouched and investigate
the identified endgame/search-consistency cases with one-change ablations.

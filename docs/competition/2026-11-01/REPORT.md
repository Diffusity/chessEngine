# Stalemate cutoff correction and timing-study outcome

The completed timing study does not establish a reason to change the default
clock policy. Experimental/default finished 5 wins, 10 draws, 5 losses;
experimental/original finished 3 wins, 11 draws, 6 losses. Each comparison has
ten colour-swapped local opening positions. All completed PGNs replay legally
with normal terminations. The first result retains only the 18 complete games
before the AC interruption plus two separately guarded replacements. The second
batch completed uninterrupted, with maximum clock-sample gap 1.026 seconds.

The experimental/default comparison used identical frozen JAR hashes. Candidate
mean move time was 4.114 seconds against 3.182 for default; mean completed depth
was 16.50 versus 16.30. Minimum recorded clocks were 11.195 versus 70.276 seconds.
These descriptive averages cover different reached positions, so they are not
equal-position estimates. Twenty games do not establish an Elo difference.
The default clock policy remains unchanged.

## Independently reproduced correctness defect

At an internal stalemate node, reverse-futility pruning returned -1070 rather
than DRAW before legal moves were generated. The recorded failing test is
`stalemate-red.txt`. A second position with a trapped knight and blocked pawns
showed that non-pawn material does not justify a null move in stalemate: the
depth-four regression returned -1879 instead of DRAW (`null-stalemate-red.txt`).
These are exact rules-based regressions, not assertions about an engine's
preferred move. They do not establish the cause of any particular match loss.

Before returning a static cutoff or attempting null-move pruning, the search
now checks for a legal move and returns DRAW if none exists. The early-exit
legal-move generator is used only when the pruning conditions qualify, avoiding
a full move-list generation at every node. Both paths already exclude check.
Ordinary full move generation still handles checkmate/stalemate, and quiescence
already checks terminal positions before stand-pat. Valid transposition-table
entries are not replaced by speculative static scores from these cutoffs.

## Validation

- Genuine red results recorded before each fix, followed by a successful package
  build: 136 Java tests, no failures, errors or skipped tests.
- 25 UCI process checks pass for default timing, and 25 for experimental timing.
- Two passes through three fixed-depth-eight positions, reversing build order:
  mean milliseconds control/candidate: start 227.5/202.5, Kiwipete 620/636.5,
  rook ending 50/50. These short warmed probes are noisy and do not establish a
  speed improvement. No substantial slowdown was observed in this limited set.
- Candidate playing-strength validation is pending a separate guarded paired
  3+2 comparison with default timing on both builds. No win-rate claim is made.

## External readiness

The account check found no active dumbEngine games. SuperChessBot1900 was not
reported online, so no challenge or bridge was started. Live rehearsals remain
pending opponent availability. No mail-send tool is connected; the authorised
organiser clarification remains unsent. PeSTO and AI-assistance eligibility
remain unresolved. Packaging must retain those disclosures. PR #1 is unmerged.

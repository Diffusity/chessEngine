# Clash-Off submission readiness

Working deadline from the supplied organiser email: November 2, noon, local
event time. The guideline/email discrepancies still require organiser confirmation.

## Release decision

Keep the default clock policy. Experimental full-soft timing scored 5 wins,
10 draws and 5 losses against the identical default build over ten opening
pairs (18 audited pre-interruption games plus two guarded replacement games).
This diagnostic sample does not establish a strength improvement.

The final executable has not yet been selected. Complete the current timed
comparison before builds or CPU-intensive tests. Then reproduce the drafted
stalemate-pruning regression, implement the smallest correct fix, run the full
Java and UCI checks, and compare performance and paired-game results. A failed
validation must not replace the last validated executable.

## Package contents

- Frozen executable JAR, with its SHA-256 hash and exact source commit.
- Source archive from an explicit allowlist of project sources, tests, build
  files and required documentation; exclude local reference-engine downloads,
  private bridge configurations, credentials, logs and unrelated files.
- Quiet launch script with a documented Java 17+ requirement. Verify it from
  a clean extracted directory whose path contains spaces. Record the actual
  Java version used in testing; the local validation runtime is JDK 25.
- Build and UCI usage instructions, tested settings and bot name `dumbEngine`.
- Honest source/parameter and AI-assistance disclosures, plus organiser replies.
- Validation summary covering legal moves, time handling, terminal positions,
  match results, known limitations and live rehearsal outcomes.

Build the final archive only after selecting and freezing the tested executable.
Check archive contents for secrets and excluded material before delivery. Do not
describe a prepared package as submitted or competition-approved.

## External checks still required

The authorised clarification draft is at
`2026-10-29/organiser-clarification.md`, addressed to `ai_club@dau.ac.in`.
No mail-sending tool is currently connected. The message remains unsent.
Published PeSTO parameters and AI-assistance eligibility remain unresolved;
attribution is not permission. Include this limitation until an answer is received.

After local timing work, check that `dumbEngine` has no active game and that the
authorised opponent `SuperChessBot1900` is online. Rehearse casual 3+2 then 10+5
using the validated private default-build configurations. Start the bridge before
challenging, cancel an unaccepted challenge after a bounded wait, and stop the
bridge after each completed game. Record an unavailable opponent as pending.

PR #1 remains unmerged. Preparing the package does not authorise merging it or
claiming that the engine will win the event.

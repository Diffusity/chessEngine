# Draft clarification for the organisers

Status: prepared for the participant to review and send. Not sent by this assistant.

Subject: Chess Engine Clash-Off: clarification on published evaluation values and submission

Hello AI Club organisers,

I am preparing an individually developed Java UCI chess engine for the Clash-Off.
I would like to clarify the following before submitting:

1. Section 1 of the competition guidelines prohibits evaluation parameters produced
   by another engine. Page 7 of the build guide says initial weights may be taken
   from established values on the Chess Programming Wiki. My current engine uses
   the published PeSTO material values and piece-square tables. Are those specific
   parameters permitted when declared, or must I replace them with my own values?
   I can provide the relevant source file for review.
2. The email specifies solo participation and submission by 2 November at noon,
   whereas the guidelines refer to teams and submission on the event day. I am
   proceeding as a solo participant and planning to submit before 2 November noon.
   Please confirm the submission channel and whether the challenge token is supplied
   separately on 3 November as described in the guidelines.
3. Is a ready-to-run executable JAR with a quiet launch script and a documented
   Java 17+ requirement acceptable for the executable requirement? The complete
   source and build instructions will be included.
4. What forms of AI-assisted debugging or code generation are allowed under the
   requirement that chess intelligence is the participant's own work, and what
   disclosure is required? I am using an AI coding assistant for review, tests,
   and proposed fixes and want to ensure that this is declared appropriately.

Thank you.

## Interim engineering decision

Do not describe the current evaluation as competition-approved. Do not assume
that attribution alone permits otherwise prohibited engine parameters. Preserve
the existing baseline for diagnostics; prepare an independently designed evaluation
if the organisers do not explicitly permit these values. Do not copy search or
evaluation code from Calvin or another engine. Do not use externally engine-labelled
training positions. Data from a version using disputed parameters is not a clean
fallback training source while that provenance question is unresolved.

Keep any organiser reply with the submission records. Do not publish private API
tokens in this document, the README, benchmark outputs, or source archives.

## Evidence checked

- `src/main/java/com/titan/evaluation/PieceSquareTables.java`: PeSTO material/PST values.
- `INTERVIEW_GUIDE.md`, around line 743: explicitly identifies the published tables.
- `README.md`: currently lacks the required source declarations and bot username.
- The local `calvin-ref/` and downloaded Calvin archive are reference material;
  their presence alone is not evidence that the submitted core was copied. A full
  provenance audit is still outstanding.
- Original documents: the three PDFs supplied in the participant's Downloads
  folder, reviewed in this chat. The email deadline is the conservative working
  deadline; inconsistencies need an organiser answer, not an assumption of permission.

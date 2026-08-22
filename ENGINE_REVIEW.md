# Titan Chess Engine — Full Code Review & Improvement Roadmap

> **Review date:** 2026-08-22 (updated after Calvin 5.1.0 reference analysis)
> **Repository:** `D:\chessEngine`
> **Git branch under review:** `temp/comp` (working tree clean, HEAD `b3992bf`)
> **Current strength (reported):** ~2350 Elo rapid / ~2350 classical
> **Measured baseline (this machine):** `bench 8` → **720,694 nodes in 2,623 ms ≈ 275k NPS**
>
> ## 🎯 Target: **3400 Elo (acceptable band 3300–3500)** — calibrated against Calvin 5.1.0
> Reference engine: **[Calvin](https://github.com/kelseyde/calvin-chess-engine)** v5.1.0 — rated **~3400–3500 CCRL**, written in **the same language (Java 17)**, source extracted to `calvin-ref/calvin-chess-engine-5.1.0/`. Every recommendation below now cites the exact Calvin class that implements it, so you can port proven code instead of inventing from scratch.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [The Reference Engine: What Calvin Is and Why It Matters](#2-the-reference-engine-what-calvin-is-and-why-it-matters)
3. [Critical Bugs — Fix First (game-losing)](#3-critical-bugs--fix-first-game-losing)
4. [Performance Findings — Speed & NPS](#4-performance-findings--speed--nps)
5. [Search Accuracy Findings — closing the gap to Calvin's search](#5-search-accuracy-findings)
6. [Evaluation Findings — HCE today, NNUE tomorrow](#6-evaluation-findings--hce-today-nnue-tomorrow)
7. [Time Management — port Calvin's model](#7-time-management--port-calvins-model)
8. [Infrastructure, Testing & Methodology](#8-infrastructure-testing--methodology)
9. [Roadmap to 3400 ±100](#9-roadmap-to-3400-100)

---

## 1. Executive Summary

Titan already has the right skeleton for ~2350: bitboards + make/unmake, PVS/negamax, TT,
NMP, LMR, killers/history/counters, SEE-pruned quiescence, tapered PeSTO-style HCE.

**Why Calvin is the perfect benchmark:** it is a *Java* engine with a *classical alpha-beta*
architecture that reached **3400–3500 CCRL**. Its release history is a measured ladder out of
exactly your range:

| Calvin version | Est. Elo | What changed |
|---|---|---|
| 3.2.0 | ~2250 | ← roughly where Titan is today |
| 3.3.0 | ~2450 | HCE improvements + tuning |
| 3.4.0 | ~2500 | more classical work |
| **4.0.0** | **~3000** | **switched HCE → NNUE (+500!)** |
| 4.3.0 | ~3300 | search tuning, bigger net |
| 5.1.0 | **~3400–3500** | better SEE unlocked pruning suite, 1024-hidden bucketed net |

Two structural conclusions follow:

1. **Speed:** Titan's 275k NPS must become millions. Every technique gap compounds with speed.
2. **Evaluation:** the single biggest jump in Calvin's history was HCE → NNUE (+~500 Elo).
   Titan should plan the same transition after fixing its foundation.

Everything below is ordered: correctness first, speed second, then the search/eval feature
ladder — each item cross-referenced to Calvin's working implementation.

---

## 2. The Reference Engine: What Calvin Is and Why It Matters

Source location: `calvin-ref/calvin-chess-engine-5.1.0/src/main/java/com/kelseyde/calvin/`

| Component | Calvin's approach | File(s) |
|---|---|---|
| Board | Bitboards + mailbox, incremental state | `board/Board.java`, `board/BoardState.java` |
| Movegen | **Fully legal** via checker mask + pin rays + capture/push masks — zero make/unmake testing | `movegen/MoveGenerator.java` (887 lines, heavily commented) |
| Sliding attacks | **Magic bitboards** with published constants | `movegen/Attacks.java` (`ROOK_MAGICS`, `BISHOP_MAGICS`, `MagicLookup`) |
| TT | Two flat `long[]`, **16 B/entry**, **4-way buckets**, age+depth replacement, static-eval cached inside entry | `tables/tt/TranspositionTable.java`, `HashEntry.java` |
| Search | PVS + ID, aspiration w/ widening & fail-high depth reduction, IIR, MDP, razoring, improving-aware margins | `search/Searcher.java` (810 lines) |
| Pruning | Adaptive NMP, futility (prune+reduce bands), RFP (improving margins), history leaf pruning, LMP, **SEE quiet AND noisy pruning** | `Searcher.java` lines 262–462 |
| Ordering | Staged `MovePicker` (GOOD_NOISY / BAD_NOISY / QUIET), MVV + capture-history, killers, counters, **1&2-ply continuation history** | `search/picker/*.java`, `tables/history/*` |
| Eval | **NNUE**: `(768×4 buckets→1024)×2→1`, SCReLU, horizontal mirror, quantised 255/64, scale 400, Finny-table refresh, **Java Vector API SIMD** | `evaluation/NNUE.java`, `Network.java`, `Accumulator.java`, `activation/Screlu.java` |
| Eval cache | Static eval stored in TT + **correction history** (pawn & non-pawn-material keyed) | `tables/correction/*.java` |
| Threads | **Lazy SMP** over per-thread `ThreadData`, shared lockless-friendly TT | `search/ParallelSearcher.java`, `ThreadData.java` |
| Time mgmt | soft/hard bounds, **best-move-stability, score-stability, node-TM scaling**, UCI-overhead subtraction | `search/TimeControl.java` |
| Tunables | **~70 SPSA-tuned parameters** exposed via UCI `setoption`, incl. full LMR table rebuild | `engine/EngineConfig.java` |
| Training | bullet trainer, 2.7B self-rescored Leela positions | `utils/train/TrainingDataScorer.java` |

Release notes for 5.1.0 (worth reading as a checklist of "what actually gains Elo at 3300+"):
SEE rewrite enabled SEE-pruning everywhere (+106/+124 Elo alone), cut-node IIR, cut-node TT
cutoff condition, TT-hit condition inside NMP, exact-score always overwrites TT, no qsearch
drop-in while in check, repetition detection including pre-root positions.

---

## 3. Critical Bugs — Fix First (game-losing)

### 3.1 `bestmove 0000` forfeit when search stops early

**Where:** `src/main/java/com/titan/engine/Engine.java:87-92`.

If `stop` arrives or hard-limit trips before iteration 1 completes, `iterativeDeepening()`
returns null best move and Titan prints `bestmove 0000` → automatic loss on Lichess.

**Calvin reference:** `Searcher.java:72-75,154-157` — generates root moves up-front; if time
expires before any iteration completes, plays `rootMoves.get(0)`; if only one legal move
exists, plays it instantly without searching (`handleOnlyOneLegalMove`). Copy both behaviors.

**Fix:** track `lastGoodMove` across iterations; fall back to TT move or first legal move;
never emit `0000` unless there are truly no legal moves.

### 3.2 En-passant square hashed unconditionally

**Where:** `Board.java:253-257` + hash XORs at `Board.java:198,235`.

EP square is set/hashed on every double push even when no enemy pawn can capture → identical
positions get different keys → TT misses + failed repetition detection (wrong draw assessment
in search). Typical cost: 10–30 Elo.

**Fix:** set EP square only if `Attacks.PAWN_ATTACKS[opponent][epSq] & myPawns != 0`.
Mirror the rule in the FEN parser. (Calvin hashes EP file the same conditional way.)

### 3.3 TT generation counter wraps at 127 searches

`TranspositionTable.java:12` uses `byte currentAge`; wrap mass-invalidates the table in long
sessions. Use `int`/masked fields like Calvin (`TranspositionTable.age`, packed into key bits).

### 3.4 Search-thread race window in `stopSearch()`

`Engine.stopSearch()` joins max 2 s then proceeds; a lingering thread can race a new search on
the same `Board`. Join unconditionally (the 2048-node stop-check guarantees quick exit) or
refuse new searches until the old thread dies. Calvin avoids the problem structurally: each
thread owns its own Board/searcher instances (`ParallelSearcher.initThread`).

### 3.5 Bench/info time artifact

`SearchLimits.startTime` unset in bench mode → nonsense `time`/`nps` in info lines. Initialize
lazily in `Searcher.search()`.

---

## 4. Performance Findings — Speed & NPS

Measured baseline **~275k NPS**; Calvin-class Java engines run several million. Items 4.1–4.4
are worth a combined **5–15×** and are prerequisites for everything in §5–§6.

### 4.1 ★★★ Replace make/unmake legality filtering with legal move generation

**Where:** `MoveGenerator.generateLegalMoves` (`MoveGenerator.java:23-42`) makes/unmakes every
pseudo-legal move and calls `isInCheck`; the searcher then makes the chosen move again.
Triples make/unmake cost at every node and allocates lists/objects twice.

**Calvin reference:** `movegen/MoveGenerator.java` computes once per call:
- `checkersMask` + count (double check ⇒ king moves only),
- `pinRayMasks[64]` per pinned piece,
- `pushMask` / `captureMask` derived from the checker (block ray via `Ray.between`),
then filters every generated move with pure bit ops against those masks. No make, no unmake,
no attack scans per candidate. Special-cased EP legality and castling-through-check are
handled inline. Port this design nearly verbatim — it is the highest-value single change in
this document.

**Expected gain:** 3–6× NPS.

### 4.2 ★★★ Implement magic bitboards (README currently overstates)

**Where:** `Attacks.bishopAttacks/rookAttacks` (`Attacks.java:129-195`) scan square-by-square
with nested helpers. These sit under movegen, `isAttacked`, eval mobility, and SEE.

**Calvin reference:** `Attacks.java` — published `ROOK_MAGICS`/`BISHOP_MAGICS` constant tables,
per-square masks/shifts, one shared attack array, `MagicLookup` records. Straightforward to
port since both projects share square mapping conventions (a1=0).

**Expected gain:** +30–80% overall NPS.

### 4.3 ★★★ Eliminate allocation churn in the hot path

Per node today: 2 ArrayLists, one immutable `Move` object per generated move, `int[]`
score arrays ×2, `new BoardState()` per make (`Board.java:194` → `state.copy()`), and a fresh
`int[32]` gain array per SEE call (`SEE.java:26`) — and SEE runs for every capture at every
node.

**Fix (Calvin-proven shapes):**
1. Flatten make/unmake history into primitive arrays on `Board` (Calvin stores undo data in
   parallel arrays; no object copies).
2. Keep `Move` as a 16-bit-encoded wrapper but reuse instances where possible / move toward
   int-encoded lists per ply in your `SearchStack` (Calvin's `SearchStack.SearchStackEntry`
   holds per-ply state incl. searched-move lists).
3. Preallocate SEE scratch buffers per searcher instance (Calvin's SEE uses an internal
   swap-list without heap allocation).

**Expected gain:** +20–50% NPS, fewer GC pauses.

### 4.4 ★★ Rebuild the Transposition Table around flat arrays + buckets

**Where:** `TranspositionTable.java` uses one `HashEntry` object per slot (~40 B real vs 32 B
assumed), pointer-chasing probes, write-on-read age refresh.

**Calvin reference (`tables/tt/TranspositionTable.java`)** — copy this design:
- `long[] keys` + `long[] values`, exactly 16 bytes/entry;
- **4-entry buckets** sharing one index;
- index = fold `key ^ (key>>>32)` masked positive, bucket-aligned (`& -BUCKET_SIZE`);
- key word packs validation bits + staticEval + age; value word packs score + move + flag + depth;
- replacement priority: empty slot → same-key with `depth >= stored − 4` (keep old move if new
  has none) → **always overwrite on EXACT** → oldest age → shallowest depth;
- mate scores adjusted on store/retrieve by ply;
- proper `fill()` sampling (counts non-null keys only).

**Expected gain:** +5–15% NPS, materially higher hit rate, and it unlocks safe Lazy SMP later.

### 4.5 ★★ Quiescence must generate captures directly

`generateCaptures` regenerates *all* pseudo-legal moves then filters, still with make/unmake
legality tests. QSearch is most of the tree. Write dedicated capture/promo generators filtered
by the §4.1 masks. **Calvin:** `MoveGenerator.MoveFilter.CAPTURES_ONLY` path +
`QuiescentMovePicker`.

### 4.6 ★★ Stop computing SEE twice per capture

`MoveOrderer.scoreMoves` SEEs every capture for ordering (`MoveOrderer.java:42-47`), then
quiescence SEEs the same move again (`Searcher.java:429`). Calvin scores captures as
`victimValue + captureHistory/8`, splits them into good/bad noisies with a *single* SEE whose
threshold is derived from that score (`MoveScorer.scoreNoisy`: `threshold = −score/4 + seeNoisyOffset`),
and reuses the verdict for both ordering and pruning.

### 4.7 ★ Evaluation: make it incrementally cheap (and later, NNUE-updatable)

Full PST/material/mobility/phase recomputation per call (`Evaluator.java`) is expensive.
Short term: maintain material+PST+phase incrementally in make/unmake (same pattern as your
Zobrist updates). Medium term this matters even more because an NNUE accumulator needs exactly
these hooks — Calvin's `NNUE.makeMove/unmakeMove` wraps board.makeMove in the search loop
(`Searcher.java:465-469,501-502`).

### 4.8 Minor speed items

- **LMP does make→isInCheck→unmake just to test givesCheck** (`Searcher.java:290-299`) — compute
  checks cheaply from attack masks pre-make.
- **LMR calls `Math.log` twice per quiet move** (`Searcher.java:306`) — use a table
  (`config.lmrReductions[capture?1:0][depth][movesSearched]` in Calvin, rebuilt when tunables change).
- **`nodes++` after makeMove** — count on node entry like Calvin for comparable NPS stats and
  correct per-root-move accounting (needed for node-TM, §7).
- Mailbox `Piece[]` + `boolean[] colors` → single byte-code array removes null checks everywhere.

---

## 5. Search Accuracy Findings

Each item names the Calvin implementation to study/port. Order = expected Elo-per-effort.

### 5.1 Restructure the move loop around a staged MovePicker + move types

Titan sorts whole arrays up-front. Calvin's `MovePicker` generates lazily in stages (TT move →
good noisy → killer/counter → quiets → bad noisy), tags each move `GOOD_NOISY/BAD_NOISY/QUIET`,
supports `setSkipQuiets()` which futility/LMP/history pruning flip mid-loop. This is the
substrate that makes the rest of §5 clean to implement.

### 5.2 Pruning suite (port order by ROI)

| Technique | Calvin anchor | Notes |
|---|---|---|
| **Mate Distance Pruning** | `Searcher.java:194-198` | trivial, do first |
| **IIR instead of your IID** | `Searcher.java:228-236` | Titan wastes a whole search on IID then discards it; IIR just does `depth -= 1` when no TT move at PV/cut nodes (`iirDepth ≥ 3`) |
| **Razor** | `Searcher.java:300-310` | qsearch verify when `staticEval + razorMargin*depth < alpha` at low depth |
| **RFP with improving + dual margins** | `Searcher.java:274-298` | prune returns `(staticEval+beta)/2`; near-miss applies dynamic *reduction* instead |
| **Adaptive NMP** | `Searcher.java:312-343` | `r = nmpBase + depth/nmpDivisor + min((eval−beta)/scale, maxReduction)`; guarded by `nullMoveAllowed` flag (no consecutive NMP), TT condition `(!ttHit || cutNode || ttScore ≥ beta)`, mate clamp to beta |
| **Improving heuristic** | `isImproving`, `Searcher.java:763-775` | compares static eval 2 plies back (skipping check plies); feeds RFP/LMP/NMP margins |
| **Futility with prune+reduce bands** | `Searcher.java:375-399` | strict margin prunes + sets skip-quiets; softer margin adds `futilityReduction` to LMR |
| **LMP scaled by improving** | `Searcher.java:433-445` | cutoff `depth*multiplier/(1+(improving?0:1))`, valid to depth 8 |
| **History leaf pruning** | `Searcher.java:421-431` | drop terrible-history quiets at shallow depth (threshold `margin*depth+offset`) |
| **SEE pruning of quiets AND bad noisies in main search** | `Searcher.java:447-462` | thresholds `seeQuietMargin*depth` / `seeNoisyMargin*depth²+offset`; this was worth >100 Elo in Calvin 5.1.0 once SEE was correct |

### 5.3 History stack upgrade

Titan has butterfly history + killers + counters but:
- malus loop penalizes moves skipped by pruning (`Searcher.java:355-360`) — track only
  *actually searched* quiets (Calvin accumulates `sse.searchedMoves`);
- add **capture history** `[piece][to][victim]` (feeds noisy ordering, `capthist/8`);
- add **1-ply and 2-ply continuation history** (`ContinuationHistoryTable`, summed in
  `MoveScorer.continuationHistoryScore`);
- use modern gravity bonus/malus with separate max/scale for bonus vs malus
  (`AbstractHistoryTable.gravity` + `QuietHistBonus/Malus*` tunables);
- **drop history ageing** — Calvin removed it (release note #260); reset per search instead.

### 5.4 QSearch upgrades

Port Calvin's quiescence (`Searcher.quiescenceSearch`):
- **TT probe/store at depth 0** incl. static-eval caching;
- **in-check evasion search** — generate ALL legal moves when in check (finds mates; Titan
  never enters qsearch in check thanks to unconditional extensions, which explodes the tree —
  Calvin extends per-move *inside the loop* instead, release note #279);
- **delta pruning** (`staticEval + SEE.value(victim) + dpMargin < alpha → skip`);
- **qsearch futility** using a pre-computed `futilityScore`;
- SEE threshold 0 vs 1 depending on remaining depth (`qsSeeEqualDepth`).

### 5.5 Aspiration windows: iterative widening + fail-high depth reduction

Titan re-searches with infinite window on any miss. Calvin (`Searcher.java:120-149`):
fail-low → `alpha -= window; beta = (alpha+beta)/2; window *= 2`; fail-high → `beta += window;
window *= 2` **plus reduce next-searched depth by 1 (up to maxReduction)**. Cheap and effective.

### 5.6 TT interaction details worth copying

- **TT-depth extension:** on a bound-only hit with sufficient depth in non-PV, extend depth by
  1 up to `ttExtensionDepth` (avoids redundant re-searches);
- **cut-node condition in TT cutoff** (`ttEntry.score() <= alpha || cutNode`) — release note #269;
- **store rawStaticEval in TT** and reuse instead of re-running eval;
- **don't apply correction history when a ttMove exists** (#267).

### 5.7 Root handling

Only-one-legal-move instant play (`handleOnlyOneLegalMove`); per-root-move node accounting
(`td.addNodes(move, …)`) feeding node-TM; seldepth reporting in UCI info; bestmove/score
stability trackers feeding time management (§7).

---

## 6. Evaluation Findings — HCE today, NNUE tomorrow

### 6.1 Short term (HCE polish while the search catches up)

- **Tempo bonus** (+15–25 cp MG) — absent today.
- **King safety** is a stub (pawn shield −10). Add attacker-count king-ring units + open-file
  penalties, or accept the HCE ceiling and go straight to NNUE.
- **Passed pawns**: add EG king-proximity scaling, connected passers, rook-behind-passer.
- **Texel/SPSA tune everything you keep.**

### 6.2 ★★★ The big one: switch to NNUE like Calvin did

Calvin's measured jump at exactly this step: **~2500 → ~3000**. Titan should plan for it after
§4 (speed) lands, because NNUE throughput depends on it.

Architecture to replicate (from `evaluation/NNUE.java`):
- Inputs: 768 (12 piece-types × 64 squares) × **4 king-buckets**;
- Hidden: **1024 neurons × 2 perspectives**, accumulator updated incrementally on make/unmake;
- Activation: **SCReLU** (squared-clamped-ReLU — current SOTA for small nets);
- Output quantisation `{255, 64}`, eval scale 400, horizontal mirroring halves net size;
- **Finny tables** for cheap accumulator refreshes on king moves;
- Run the hot accumulation through **Java Vector API** (`jdk.incubator.vector` — see Calvin's
  launch command; this is how a JVM engine hits multi-MNPS eval);
- Train with **bullet** on self-rescored data (Leela dumps re-scored by your own search —
  Calvin started 1.2B, 5.1.0 uses 2.7B positions; `utils/train/TrainingDataScorer.java` shows
  the rescore pipeline).

Prerequisites checklist before starting NNUE: incremental make/unmake hooks (done in §4.3/4.7),
fast attacks (§4.2), stable search to generate training data (§5), SPRT harness (§8).

---

## 7. Time Management — port Calvin's model

Titan's current scheme (`SearchLimits.java`) is static: `remaining/30 + inc·¾`, deepen only
below half the soft limit, 10 ms floors. Calvin's `TimeControl` is the blueprint:

```
base      = time * timeFactor/100        (≈ 5%)   +  inc * incrementFactor/100   (≈ 75%)
softLimit = base * softTimeFactor        (≈ 0.66)
hardLimit = base * hardTimeFactor        (≈ 2.0)  − uciOverhead (ms, checked during search)
```

Then the soft limit is *scaled dynamically* each iteration by three factors (clamped to
`softTimeScaleMin..Max`, i.e. 12%–250%):

1. **Best-move stability** — table `{250,120,90,80,75}%` for 0–4 consecutive stable iterations;
2. **Score stability** — `{125,115,100,94,88}%`;
3. **Node-TM** — fraction of nodes spent on the best move: `(nodeTmBase − fraction) * scale`.

Implementation notes: per-root-move node counts come from `ThreadData.addNodes`; hard limit is
checked every 4096 nodes (`nodes % 4096 == 0` guard); negative received time clamps to 1 s.
This whole module ports almost mechanically to Titan and is worth **+20–50 Elo in
rapid/classical specifically**.

---

## 8. Infrastructure, Testing & Methodology

1. **SPRT game testing is non-negotiable at this level.** Calvin validates every change
   (their release notes quote pentanomial SPRT results over 4k games). Set up
   [fastchess](https://github.com/Disservin/fastchess) or OpenBench; ship nothing without LLR ≥ 95%.
   Note Calvin 5.1.0's own lesson: their biggest gain (+106–124) came from *fixing a bugged SEE* —
   correctness of primitives gates everything else.
2. **Perft divide in CI** — extend your existing `perft_suite.epd` test to the 6 standard
   positions through depth 5–6 (Calvin ships kiwipete/start/en-passant-funhouse suites plus a
   special-perft framework in `src/test/java/.../perft,sperft`).
3. **Expose tunables via UCI** like Calvin's `EngineConfig.Tunable` (name/min/max/default/step +
   `getTunables()`) so external SPSA/OpenBench tuning works without recompiling.
4. **Benchmark hygiene:** warm up JIT before timing; reuse Searcher+TT across positions; report
   aggregate NPS; add fixed-node mode. Calvin's `Bench` runs ~50 mixed-phase positions.
5. **Position replay cost:** `Engine.applyMoves` regenerates all legal moves per UCI move string
   (`Engine.java:63-71`) — parse flags contextually or cache last position.
6. **Later:** Syzygy tablebases, opening book/variety, ponder support (Calvin supports pondering
   via `config.pondering` gating stop checks).

---

## 9. Roadmap to 3400 ±100

Elo estimates are anchored to Calvin's measured releases/tests wherever possible.

### Phase 0 — Stop the bleeding (days; protects existing rating)
| Item | Ref | Impact |
|---|---|---|
| bestmove fallback + single-reply shortcut | §3.1 | stops forfeits |
| Conditional EP hashing | §3.2 | +10–30 |
| Age-wrap, stop-race, bench-time fixes | §3.3–3.5 | stability |

### Phase 1 — Speed floor: reach Calvin-parity movegen/TT (1–3 weeks)
| Item | Ref | Expected |
|---|---|---|
| Legal movegen (checker/pin masks) — port Calvin design | §4.1 | 3–6× NPS |
| Magic bitboards | §4.2 | +30–80% NPS |
| Zero-alloc make/unmake + move storage | §4.3 | +20–50% NPS |
| Flat 16-byte bucketed TT | §4.4 | +5–15% NPS, hit-rate ↑ |
| Direct capture gen + single-SEE ordering | §4.5–4.6 | +10–20% NPS |

Target checkpoint: **2–5M NPS** ⇒ deeper search at equal clock ≈ **+150–250 Elo** (2350 → ~2550,
matching Calvin 3.4 territory).

### Phase 2 — Search parity with Calvin 4.x (3–6 weeks, ship each item behind SPRT)
MDP → IIR → razoring → improving-aware RFP/futility → adaptive NMP → LMP/history-pruning →
staged MovePicker → capture + 1&2-ply continuation history → qsearch TT/delta/evasions →
aspiration widening + fail-high reduction → SEE quiet/noisy pruning in main search.
Cumulative expectation: **+100–200 Elo** (→ ~2700–2850).

### Phase 3 — NNUE evaluation (6–10 weeks incl. training pipeline) — the Calvin 4.0 moment
Vector-API inference, bucketed 768→1024×2 SCReLU net, bullet training on self-rescored data.
Calvin gained **~+500 Elo** here; assume conservatively **+250–400** for Titan (→ ~3000–3200).

### Phase 4 — Grind like Calvin 4.x→5.x (ongoing)
Correction history, Finny refreshes, bigger/bucketed nets, SPSA on all ~70 params, node-TM +
stability scaling, Lazy SMP (+80–120 @4T), Syzygy, SEE/ordering refinements. This is the
3000 → 3400 stretch; expect slow, verified increments — Calvin needed a year of disciplined
testing for it.

**Bottom line:** Phase 0 stops rating loss; Phase 1 buys the raw speed every later feature
needs; Phase 2 clones a proven 3000-Elo search; Phase 3 replicates the single largest jump in
the reference engine's history; Phase 4 is steady-state engineering. With Calvin 5.1.0 sitting
locally as both blueprint and sparring partner, the 3400 ±100 target is realistic — the
reference author got there in Java with the same architecture family Titan already has.

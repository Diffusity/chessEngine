# Titan Engine — Implementation Plan

> Companion to `ENGINE_REVIEW.md`. Ordered by Elo-per-hour (time efficiency).
> Safety net for every step: `mvn test` (PerftTest suite must stay green) + `bench`.

## Constraint: no GPU available
- NNUE **inference**: pure CPU via Java Vector API (`jdk.incubator.vector`) — same as Calvin.
- NNUE **training** (choose when Phase 2 lands):
  1. Cloud GPU bursts (recommended): RTX-class ~$0.40–1.50/hr; full 768×1024×2 SCReLU net ≈ $50–150 one-off.
  2. Local CPU fallback: smaller net (768→256×2), ~100M positions, bullet CPU mode — slower, ceiling ≈ +250–300 Elo.
  3. Either way: dataset rescoring runs locally on CPU overnight (parallel across cores).

## Phase 0 — correctness (day 1) ✅ DONE (2026-08-22)
- [x] P0-1 `Engine`/`Searcher`: never print `bestmove 0000` while legal moves exist (root fallback + TT/first-move fallback). Ref: Calvin `Searcher.java:72-75,154-157`
- [x] P0-2 `Board.makePawnDoubleMove`: set EP square only when an enemy pawn can capture (+ matching conditional hash in `Zobrist.computeKey`)
- [x] P0-3 `TranspositionTable`: age `byte` → `int` (wrap-safe) (`HashEntry.age` widened too)
- [x] P0-4 `Engine.stopSearch`: join unconditionally in a bounded loop — no race with new search
- [x] P0-5 `Searcher.search`: lazily initialize `limits.startTime`; Bench rewritten (JIT warmup, reused Searcher/TT, `init()` called)

## Phase 1 — speed floor (week 1–2)
- [x] P1-A Zero-alloc make/unmake: BoardState **free-list recycling** (`copyInto` pooled snapshots).
      ⚠ Lesson learned: naive slot-copy aliases the live state after unmake and destroys the
      snapshot on the next make at the same ply — free-list guarantees the snapshot object is
      distinct from `state`. Verified via perft + key round-trip instrumentation.
- [x] P1-B Magic bitboards for rook/bishop (Calvin's published constants), queenAttacks composed.
      Verified by new `AttacksMagicTest`: exact equality vs ray-scan over structured + 200k random occupancies.
- [x] P1-C Legal movegen: checker mask + pin rays + push/capture masks; make/unmake legality loop DELETED.
      King moves tested via occupancy-without-king (fixes latent castle-through-vacated-check bug);
      EP validated by dedicated occupancy simulation; double-check early-exit.
      Debugged via differential comparator vs make/unmake reference — caught two real bugs
      (inverted enemy-pawn attack pattern; double-push derived from evasion-filtered singles).
- [x] P1-D Quick wins done: SEE preallocated swap buffer; EP lookup hoisted from pawn loop.
      Still open: LMR reduction table, count-nodes-at-entry, dedicated capture generator
      (generateCaptures now mask-based but still builds full quiet targets before filtering).

### Measured so far (same machine, bench depth 8, 8 positions):
| Stage | NPS | Notes |
|---|---|---|
| Baseline (original code) | ~275k (noisy, no warmup) | 720,694 nodes |
| After Phase 0 | nodes −23.5% | conditional EP → TT hits |
| After P1-A + P1-B + partial D | ~326–347k | magics ≈ +25% |
| **After P1-C legal movegen** | **~950–1000k** | **≈3× speedup**, 564,863 nodes |

Cumulative vs session start: **≈3.6× NPS** at equal node counts ⇒ roughly one extra ply of
search per move at equal clock. All 92 tests green incl. full perft suite + magic-equivalence.

Checkpoint: ≥ 2M NPS on `bench 8`; all perft suites green.

## Phase 2 — search parity (weeks; ship each behind SPRT)
- [x] **Batch 1 (2026-08-22)**: Mate-distance pruning; IIR replacing IID (with cutNode
      plumbing through PVS/NMP); improving heuristic feeding RFP/LMP margins; adaptive NMP
      (base + depth/6 + eval-scale R, mate clamp, beta-mate guard); LMR precomputed table +
      never-reduce-checks; aspiration windows with geometric widening AND fail-high depth
      reduction; history malus applied only to actually-searched quiets; nodes counted at
      node entry; LMP extended to depth 6.
      Measured: startpos d10 nodes 307k→144k (−53%), time 559ms→427ms; bench8 nodes −57%;
      tactics position now finds mate-in-3 at d8 (baseline missed it). All 92 tests green.
- [ ] Batch 2: qsearch TT probe/store + delta pruning + in-check evasion generation;
      TT static-eval caching; staged MovePicker w/ skip-quiets; capture history;
      1&2-ply continuation history; check-extension capping
- [ ] Batch 3: SEE quiet/noisy pruning in main search; singular extensions; multicut

## Phase 3 — NNUE (after Phase 2 stabilizes)
- [ ] Accumulator infra wired into make/unmake; Vector API inference kernel
- [ ] Training-data generator (self-rescore pipeline, CPU parallel)
- [ ] Net v1: 768→256×2 SCReLU trained locally (CPU) or cloud-rented GPU for 1024-hidden

## Phase 4 — scale
- [ ] Correction history; SPSA tunables via UCI (Calvin `Tunable` pattern); Lazy SMP; Syzygy

## Verification protocol per change
1. `mvn -q test` — perft + unit suites green
2. `java -jar target/...jar bench 8` — NPS regression check
3. SPRT (fastchess) ≥ 95% LLR before merging any Phase 2+ feature

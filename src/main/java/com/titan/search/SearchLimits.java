package com.titan.search;

import java.util.function.LongSupplier;

/** Search constraints, using a monotonic clock and millisecond UCI budgets. */
public class SearchLimits {
    // Negative means absent; an explicit zero clock is an exhausted budget.
    public long wtime = -1, btime = -1;
    public long winc, binc;
    public int movestogo;
    public int depth;
    public long nodes;
    public long movetime = -1;
    public boolean infinite;
    public long softLimit, hardLimit, startTime;

    // Small local cancellation reserve; the bridge owns its network reserve.
    private static final long SAFETY_RESERVE_MS = 10;
    private final LongSupplier nanoClock;
    private boolean timed;

    public SearchLimits() { this(System::nanoTime); }
    SearchLimits(LongSupplier nanoClock) { this.nanoClock = nanoClock; }

    public static SearchLimits infinite() {
        SearchLimits limits = new SearchLimits();
        limits.infinite = true;
        return limits;
    }

    public static SearchLimits fixedDepth(int depth) {
        SearchLimits limits = new SearchLimits();
        limits.depth = depth;
        return limits;
    }

    public static SearchLimits moveTime(long timeMs) {
        SearchLimits limits = new SearchLimits();
        limits.movetime = Math.max(0, timeMs);
        return limits;
    }

    public void init(boolean whiteToMove) {
        startTime = nanoClock.getAsLong();
        long remaining = whiteToMove ? wtime : btime;
        long increment = Math.max(0, whiteToMove ? winc : binc);
        timed = !infinite && (remaining >= 0 || movetime >= 0);
        if (!timed) {
            softLimit = hardLimit = Long.MAX_VALUE;
            return;
        }

        long usable = remaining >= 0 ? Math.max(0, remaining - SAFETY_RESERVE_MS) : Long.MAX_VALUE;
        if (movetime >= 0) {
            softLimit = hardLimit = Math.min(movetime, usable);
            return;
        }

        int movesLeft = movestogo > 0 ? movestogo : 40;
        // 60% of increment influences the target, but never expands spendable time.
        long incrementShare = increment / 5 * 3 + increment % 5 * 3 / 5;
        long base = usable / movesLeft;
        long target = base > Long.MAX_VALUE - incrementShare ? Long.MAX_VALUE : base + incrementShare;
        softLimit = Math.min(target, usable);
        long extended = softLimit > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : softLimit * 2;
        hardLimit = Math.min(usable / 5, extended);
        softLimit = Math.min(softLimit, hardLimit);
    }

    public long elapsedMillis() {
        return Math.max(0, (nanoClock.getAsLong() - startTime) / 1_000_000);
    }

    public boolean isTimeUp() { return timed && elapsedMillis() >= hardLimit; }

    public boolean shouldDeepen() {
        return !timed || elapsedMillis() < softLimit / 2;
    }
}

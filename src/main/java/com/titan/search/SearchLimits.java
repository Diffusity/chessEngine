package com.titan.search;

/**
 * Encapsulates search constraints from UCI `go` command and computes time allocation.
 */
public class SearchLimits {
    // UCI fields — set from "go" command
    public long wtime, btime;          // remaining time in ms
    public long winc, binc;            // increment per move in ms
    public int movestogo;              // moves until next time control (0 = sudden death)
    public int depth;                  // max depth (0 = unlimited)
    public long nodes;                 // max nodes (0 = unlimited)
    public long movetime;              // exact time per move in ms (0 = unused)
    public boolean infinite;           // infinite search (until "stop")

    // Computed limits
    public long softLimit;             // target time (try to finish within this)
    public long hardLimit;             // absolute max time (must stop here)
    public long startTime;             // nanoTime when search started

    /**
     * Factory for analysis mode (infinite).
     */
    public static SearchLimits infinite() {
        SearchLimits limits = new SearchLimits();
        limits.infinite = true;
        return limits;
    }

    /**
     * Factory for fixed-depth search.
     */
    public static SearchLimits fixedDepth(int depth) {
        SearchLimits limits = new SearchLimits();
        limits.depth = depth;
        return limits;
    }

    /**
     * Factory for move time limit.
     */
    public static SearchLimits moveTime(long timeMs) {
        SearchLimits limits = new SearchLimits();
        limits.movetime = timeMs;
        return limits;
    }

    /**
     * Computes the soft/hard limits from UCI fields based on side to move.
     */
    public void init(boolean whiteToMove) {
        startTime = System.nanoTime();

        if (infinite) {
            softLimit = Long.MAX_VALUE;
            hardLimit = Long.MAX_VALUE;
            return;
        }

        if (movetime > 0) {
            softLimit = movetime;
            hardLimit = movetime;
            return;
        }

        long remaining = whiteToMove ? wtime : btime;
        long increment = whiteToMove ? winc : binc;

        if (remaining == 0 && increment == 0 && depth == 0 && nodes == 0) {
             // no limits specified, act like infinite
             softLimit = Long.MAX_VALUE;
             hardLimit = Long.MAX_VALUE;
             return;
        }

        int movesLeft = movestogo > 0 ? movestogo : 30;
        
        softLimit = remaining / movesLeft + (increment * 3 / 4);
        hardLimit = Math.min(remaining / 4, softLimit * 3);

        // Ensure reasonable minimums if time is very low
        if (softLimit < 10) softLimit = 10;
        if (hardLimit < 10) hardLimit = 10;
    }

    /**
     * Check if hard limit exceeded.
     */
    public boolean isTimeUp() {
        if (infinite || (depth > 0 && movetime == 0 && wtime == 0 && btime == 0)) {
            return false;
        }
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        return elapsed >= hardLimit;
    }

    /**
     * Check if there is enough time for another iteration.
     */
    public boolean shouldDeepen() {
        if (infinite || (depth > 0 && movetime == 0 && wtime == 0 && btime == 0)) {
            return true;
        }
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        // Only start next depth if we've used less than half of our soft limit
        return elapsed < (softLimit / 2);
    }
}

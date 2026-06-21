package com.titan.search;

/**
 * Constants and helpers for search scores.
 */
public final class Score {
    public static final int MATE       = 100_000;
    public static final int INFINITY   = MATE + 1;
    public static final int DRAW       = 0;
    public static final int NONE       = Integer.MIN_VALUE + 1; // sentinel "no score"

    private Score() {
        // utility class
    }

    /**
     * Returns true if the score represents a forced mate.
     */
    public static boolean isMateScore(int score) {
        return Math.abs(score) > MATE - 1000;
    }

    /**
     * Returns the mate-in-N distance from a mate score.
     * Assuming positive score means we deliver mate, negative means we get mated.
     */
    public static int mateInPly(int score) {
        if (score > 0) {
            return MATE - score;
        } else {
            return MATE + score;
        }
    }

    /**
     * Returns a mate score for the given ply distance (positive = we deliver mate).
     */
    public static int mateScore(int ply) {
        return MATE - ply;
    }
}

package com.titan.tables.tt;

/**
 * Indicates the type of score stored in the transposition table.
 */
public enum HashFlag {
    /** The score is exact (from a PV node). */
    EXACT,

    /** The score is a lower bound (failed high / beta cutoff). */
    LOWER_BOUND,

    /** The score is an upper bound (failed low / all-node). */
    UPPER_BOUND,

    /**
     * No usable score — entry exists only to cache the static evaluation.
     * Such entries must never be used for TT cutoffs or move ordering.
     */
    NONE
}

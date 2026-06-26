package com.titan.tables;

/**
 * Hash table to cache the expensive pawn structure evaluation.
 * Indexed by the Zobrist pawn key.
 */
public class PawnHashTable {

    public static class PawnEntry {
        public long key;
        public int mgScore;
        public int egScore;
    }

    private final PawnEntry[] table;
    private final int mask;

    public PawnHashTable(int capacity) {
        // Ensure capacity is power of 2
        int size = 1;
        while (size < capacity) {
            size <<= 1;
        }
        
        table = new PawnEntry[size];
        for (int i = 0; i < size; i++) {
            table[i] = new PawnEntry();
        }
        mask = size - 1;
    }

    /**
     * Probes the table for the given pawn key.
     * @return the entry if found, otherwise null
     */
    public PawnEntry probe(long pawnKey) {
        PawnEntry entry = table[(int) (pawnKey & mask)];
        if (entry.key == pawnKey) {
            return entry;
        }
        return null;
    }

    /**
     * Stores a pawn evaluation result.
     */
    public void store(long pawnKey, int mgScore, int egScore) {
        PawnEntry entry = table[(int) (pawnKey & mask)];
        entry.key = pawnKey;
        entry.mgScore = mgScore;
        entry.egScore = egScore;
    }
}

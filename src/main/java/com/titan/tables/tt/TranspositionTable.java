package com.titan.tables.tt;

import com.titan.search.Score;

/**
 * Transposition Table to cache search results.
 */
public class TranspositionTable {

    private HashEntry[] table;
    private int mask;
    private byte currentAge;
    private int sizeMB;

    public TranspositionTable(int sizeMB) {
        resize(sizeMB);
    }

    /**
     * Resizes the transposition table.
     * @param sizeMB the size in megabytes
     */
    public void resize(int sizeMB) {
        this.sizeMB = sizeMB;
        // 1 MB = 1024 * 1024 bytes. 
        // HashEntry in Java is ~24-32 bytes. Let's assume 32 bytes per entry roughly.
        int entrySizeBytes = 32;
        long numEntries = ((long) sizeMB * 1024 * 1024) / entrySizeBytes;
        
        // Find highest power of 2 <= numEntries
        int capacity = 1;
        while (capacity <= numEntries) {
            capacity <<= 1;
        }
        capacity >>>= 1; // back down one step
        
        // Minimum size
        if (capacity < 1024) {
            capacity = 1024;
        }

        table = new HashEntry[capacity];
        for (int i = 0; i < capacity; i++) {
            table[i] = new HashEntry();
        }
        mask = capacity - 1;
        currentAge = 0;
    }

    /**
     * Increments the age, called at the start of a new search.
     */
    public void newSearch() {
        currentAge++;
    }

    /**
     * Clears the transposition table.
     */
    public void clear() {
        for (HashEntry entry : table) {
            entry.clear();
        }
        currentAge = 0;
    }

    /**
     * Probes the transposition table for a given key.
     * @param key the Zobrist key of the current board state
     * @param ply the current distance from root
     * @return the entry if found, otherwise null
     */
    public HashEntry probe(long key, int ply) {
        HashEntry entry = table[(int) (key & mask)];
        if (entry.key == key) {
            entry.age = currentAge; // refresh age
            return entry;
        }
        return null;
    }

    /**
     * Stores a new result in the transposition table.
     * @param key the Zobrist key of the board state
     * @param move the best move found
     * @param score the evaluation score
     * @param depth the search depth
     * @param flag the HashFlag indicating exact, lower bound or upper bound
     * @param ply the current distance from root
     */
    public void store(long key, int move, int score, int depth, HashFlag flag, int ply) {
        HashEntry entry = table[(int) (key & mask)];

        // Replacement policy:
        // 1. Exact key match (we update it if depth is >= existing, or it's an EXACT score and existing is not EXACT at same depth, etc.)
        //    Actually, simple replacement if key matches and depth >= existing or different age.
        // 2. Different key (collision) -> replace if existing is older, or new is deeper.
        
        boolean replace = false;
        
        if (entry.key == 0 || entry.key == key) {
            replace = true;
        } else if (entry.age != currentAge) {
            replace = true; // aging out
        } else if (depth >= entry.depth) {
            replace = true; // deeper search overwrites
        }

        if (replace) {
            // Adjust mate scores before storing
            int adjustedScore = score;
            if (Score.isMateScore(score)) {
                if (score > 0) {
                    adjustedScore = score + ply;
                } else {
                    adjustedScore = score - ply;
                }
            }

            entry.key = key;
            if (move != 0) { // Don't overwrite an existing good move with a null move
                entry.move = move;
            }
            entry.score = adjustedScore;
            entry.depth = (short) depth;
            entry.flag = (byte) flag.ordinal();
            entry.age = currentAge;
        }
    }

    /**
     * Gets the score from the entry, adjusting for mate scores based on current ply.
     */
    public static int getScore(HashEntry entry, int ply) {
        int score = entry.score;
        if (Score.isMateScore(score)) {
            if (score > 0) {
                return score - ply;
            } else {
                return score + ply;
            }
        }
        return score;
    }
    
    /**
     * Returns a hashfull approximation (per mille, 0 to 1000).
     */
    public int hashFull() {
        int count = 0;
        int maxChecks = Math.min(1000, table.length);
        for (int i = 0; i < maxChecks; i++) {
            if (table[i].key != 0 && table[i].age == currentAge) {
                count++;
            }
        }
        return (count * 1000) / maxChecks;
    }
}

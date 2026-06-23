package com.titan.tables.history;

/**
 * Tracks the history heuristic for quiet moves that cause cutoffs.
 */
public class HistoryTable {
    // [color: 0=White, 1=Black][fromSq][toSq]
    private final int[][][] table;

    public HistoryTable() {
        table = new int[2][64][64];
    }

    /**
     * Rewards a move that caused a cutoff.
     */
    public void addBonus(boolean white, int from, int to, int depth) {
        int colorIdx = white ? 0 : 1;
        int bonus = depth * depth;
        int current = table[colorIdx][from][to];
        
        // Gravity scaling to prevent overflow and keep relative scores normalized
        table[colorIdx][from][to] = current + bonus - (current * bonus) / 16384;
    }

    /**
     * Penalizes a move that did not cause a cutoff.
     */
    public void addMalus(boolean white, int from, int to, int depth) {
        int colorIdx = white ? 0 : 1;
        int malus = depth * depth;
        int current = table[colorIdx][from][to];
        
        // Gravity scaling
        table[colorIdx][from][to] = current - malus - (current * malus) / 16384;
    }

    /**
     * Gets the history score for a move.
     */
    public int getScore(boolean white, int from, int to) {
        int colorIdx = white ? 0 : 1;
        return table[colorIdx][from][to];
    }

    /**
     * Ages the table by halving all scores. Called at start of a new search.
     */
    public void age() {
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 64; f++) {
                for (int t = 0; t < 64; t++) {
                    table[c][f][t] /= 2;
                }
            }
        }
    }

    /**
     * Clears the table entirely.
     */
    public void clear() {
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 64; f++) {
                for (int t = 0; t < 64; t++) {
                    table[c][f][t] = 0;
                }
            }
        }
    }
}

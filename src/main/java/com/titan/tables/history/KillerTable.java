package com.titan.tables.history;

/**
 * Stores killer moves — quiet moves that caused a beta cutoff at a specific ply.
 */
public class KillerTable {
    private final int[][] killers;

    public KillerTable(int maxPly) {
        killers = new int[maxPly][2];
    }

    /**
     * Adds a move as a killer move for the given ply.
     * @param move the encoded move
     * @param ply the distance from root
     */
    public void add(int move, int ply) {
        if (ply >= killers.length) return;
        
        // Don't add if it's already the primary killer
        if (killers[ply][0] == move) {
            return;
        }
        
        // Shift primary to secondary and store new primary
        killers[ply][1] = killers[ply][0];
        killers[ply][0] = move;
    }

    /**
     * Gets the primary killer move for a ply.
     */
    public int getFirst(int ply) {
        if (ply >= killers.length) return 0;
        return killers[ply][0];
    }

    /**
     * Gets the secondary killer move for a ply.
     */
    public int getSecond(int ply) {
        if (ply >= killers.length) return 0;
        return killers[ply][1];
    }

    /**
     * Checks if a move is a killer move at the given ply.
     */
    public boolean isKiller(int move, int ply) {
        if (ply >= killers.length) return false;
        return killers[ply][0] == move || killers[ply][1] == move;
    }

    /**
     * Clears the killer table.
     */
    public void clear() {
        for (int i = 0; i < killers.length; i++) {
            killers[i][0] = 0;
            killers[i][1] = 0;
        }
    }
}

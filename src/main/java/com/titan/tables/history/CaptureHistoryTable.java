package com.titan.tables.history;

import com.titan.board.Piece;

/**
 * Capture history heuristic: how often a capture by {@code piece} landing on {@code to}
 * capturing {@code victim} caused a beta cutoff. Used alongside MVV-LVA for ordering
 * noisy moves.
 */
public class CaptureHistoryTable {

    private static final int GRAVITY_DIVISOR = 16384;
    private static final int MAX_SCORE = 8192;

    // Flattened [pieceType][toSquare][victimType] — single array access per lookup.
    private static final int VICTIMS = Piece.COUNT;
    private static final int STRIDE_V = VICTIMS;
    private static final int STRIDE_P = 64 * STRIDE_V;
    private final int[] table = new int[Piece.COUNT * 64 * Piece.COUNT];

    public void addBonus(Piece piece, int to, Piece victim, int depth) {
        update(piece.index(), to, victim.index(), Math.min(MAX_SCORE, depth * depth));
    }

    public void addMalus(Piece piece, int to, Piece victim, int depth) {
        update(piece.index(), to, victim.index(), -Math.min(MAX_SCORE, depth * depth));
    }

    private void update(int p, int to, int v, int delta) {
        int idx = p * STRIDE_P + to * STRIDE_V + v;
        int current = table[idx];
        // Gravity scaling keeps scores bounded and recent data dominant
        int updated = current + delta - (current * delta) / GRAVITY_DIVISOR;
        table[idx] = Math.max(-MAX_SCORE, Math.min(MAX_SCORE, updated));
    }

    public int get(Piece piece, int to, Piece victim) {
        return table[piece.index() * STRIDE_P + to * STRIDE_V + victim.index()];
    }

    public void clear() {
        java.util.Arrays.fill(table, 0);
    }
}

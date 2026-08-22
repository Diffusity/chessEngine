package com.titan.tables.history;

import com.titan.board.Move;
import com.titan.board.Piece;

/**
 * Continuation history heuristic (1-ply and 2-ply): how often a quiet move
 * {@code piece -> to} worked out well as a reply to the opponent's previous move(s)
 * {@code prevPiece -> prevTo}. Indexed per colour, using piece types without colour.
 * <p>
 * Storage is a single flat {@code int[]} — one array access per lookup, which matters
 * greatly on the JVM compared to nested (jagged) arrays.
 */
public class ContinuationHistoryTable {

    private static final int GRAVITY_DIVISOR = 16384;
    private static final int MAX_SCORE = 8192;

    // Index layout: ((c*6 + pp)*64 + pt)*6 + p)*64 + t
    private static final int STRIDE_P = 64;                  // to-square
    private static final int STRIDE_PT = Piece.COUNT * STRIDE_P;
    private static final int STRIDE_PP = 64 * STRIDE_PT;
    private static final int STRIDE_C = Piece.COUNT * STRIDE_PP;

    private static final int TABLE_SIZE = 2 * STRIDE_C;      // 294,912 ints (~1.2 MB)

    private final int[] table = new int[TABLE_SIZE];

    public void addBonus(boolean white, Move prevMove, Piece prevPiece, Move move, Piece piece, int depth) {
        update(white, prevMove, prevPiece, move, piece, Math.min(MAX_SCORE, depth * depth));
    }

    public void addMalus(boolean white, Move prevMove, Piece prevPiece, Move move, Piece piece, int depth) {
        update(white, prevMove, prevPiece, move, piece, -Math.min(MAX_SCORE, depth * depth));
    }

    private void update(boolean white, Move prevMove, Piece prevPiece, Move move, Piece piece, int delta) {
        if (prevMove == null || prevPiece == null || move == null || piece == null) return;
        int idx = idxOf(white, prevPiece.index(), prevMove.to(), piece.index(), move.to());

        int current = table[idx];
        int updated = current + delta - (current * delta) / GRAVITY_DIVISOR;
        table[idx] = Math.max(-MAX_SCORE, Math.min(MAX_SCORE, updated));
    }

    public int get(boolean white, Move prevMove, Piece prevPiece, Move move, Piece piece) {
        if (prevMove == null || prevPiece == null || move == null || piece == null) return 0;
        return table[idxOf(white, prevPiece.index(), prevMove.to(), piece.index(), move.to())];
    }

    public void clear() {
        java.util.Arrays.fill(table, 0);
    }

    private static int idxOf(boolean white, int prevPiece, int prevTo, int piece, int to) {
        return (white ? 0 : STRIDE_C)
                + prevPiece * STRIDE_PP
                + prevTo * STRIDE_PT
                + piece * STRIDE_P
                + to;
    }
}

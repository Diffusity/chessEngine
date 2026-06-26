package com.titan.tables.history;

import com.titan.board.Piece;

/**
 * Tracks the counter-move heuristic.
 * For a given move by the opponent, stores the response move that caused a beta cutoff.
 */
public class CounterMoveTable {
    // [color: 0=White, 1=Black][piece type][toSquare]
    private final int[][][] table;

    public CounterMoveTable() {
        table = new int[2][6][64];
    }

    /**
     * Stores a counter-move for the given previous move.
     */
    public void store(boolean whiteToMove, Piece prevPiece, int prevTo, int counterMove) {
        int colorIdx = whiteToMove ? 0 : 1;
        // Piece.index() goes from 0 (PAWN) to 5 (KING)
        table[colorIdx][prevPiece.index()][prevTo] = counterMove;
    }

    /**
     * Gets the counter-move for the given previous move.
     */
    public int get(boolean whiteToMove, Piece prevPiece, int prevTo) {
        if (prevPiece == null) return 0;
        int colorIdx = whiteToMove ? 0 : 1;
        return table[colorIdx][prevPiece.index()][prevTo];
    }

    /**
     * Clears the table entirely.
     */
    public void clear() {
        for (int c = 0; c < 2; c++) {
            for (int p = 0; p < 6; p++) {
                for (int t = 0; t < 64; t++) {
                    table[c][p][t] = 0;
                }
            }
        }
    }
}

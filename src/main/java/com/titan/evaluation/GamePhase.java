package com.titan.evaluation;

import com.titan.board.Board;
import com.titan.board.Piece;

/**
 * Calculates the current game phase based on non-pawn material remaining on the board.
 * <p>
 * Phase 24 represents the opening (all pieces on the board).
 * Phase 0 represents a pure pawn endgame.
 * <p>
 * Used for tapered evaluation to seamlessly blend middlegame and endgame heuristics.
 */
public final class GamePhase {

    // Piece phase weights (pawns are 0, kings are 0)
    public static final int KNIGHT_WEIGHT = 1;
    public static final int BISHOP_WEIGHT = 1;
    public static final int ROOK_WEIGHT   = 2;
    public static final int QUEEN_WEIGHT  = 4;

    // 4 Knights (4) + 4 Bishops (4) + 4 Rooks (8) + 2 Queens (8) = 24
    public static final int MAX_PHASE = 24;

    private GamePhase() {
        // Utility class
    }

    /**
     * Calculates the game phase for the given board.
     * @param board the current board state
     * @return an integer between 0 (endgame) and 24 (opening)
     */
    public static int calculate(Board board) {
        int phase = 0;

        phase += countBits(board.getBitboard(Piece.KNIGHT, true)) * KNIGHT_WEIGHT;
        phase += countBits(board.getBitboard(Piece.KNIGHT, false)) * KNIGHT_WEIGHT;

        phase += countBits(board.getBitboard(Piece.BISHOP, true)) * BISHOP_WEIGHT;
        phase += countBits(board.getBitboard(Piece.BISHOP, false)) * BISHOP_WEIGHT;

        phase += countBits(board.getBitboard(Piece.ROOK, true)) * ROOK_WEIGHT;
        phase += countBits(board.getBitboard(Piece.ROOK, false)) * ROOK_WEIGHT;

        phase += countBits(board.getBitboard(Piece.QUEEN, true)) * QUEEN_WEIGHT;
        phase += countBits(board.getBitboard(Piece.QUEEN, false)) * QUEEN_WEIGHT;

        // Ensure phase doesn't exceed MAX_PHASE (e.g., in case of crazy promotions)
        return Math.min(phase, MAX_PHASE);
    }

    private static int countBits(long bitboard) {
        return Long.bitCount(bitboard);
    }
}

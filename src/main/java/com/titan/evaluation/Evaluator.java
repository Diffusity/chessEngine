package com.titan.evaluation;

import com.titan.board.Board;
import com.titan.board.Piece;
import com.titan.board.Bits;

/**
 * Static evaluation function for the Titan Chess Engine.
 * <p>
 * Computes a heuristic score for a given board position using a tapered evaluation.
 * Tapered evaluation smoothly blends a middlegame score and an endgame score
 * based on the amount of material remaining on the board.
 * <p>
 * The score is returned from the perspective of the side to move (positive means
 * the side to move is winning).
 */
public class Evaluator {

    public static final int BISHOP_PAIR_BONUS = 30;

    /**
     * Evaluates the given board position.
     *
     * @param board the current board state
     * @return the evaluation score in centipawns (from the side-to-move's perspective)
     */
    public static int evaluate(Board board) {
        int mgScore = 0;
        int egScore = 0;

        // 1. Piece-Square Tables (PST) and Material
        for (Piece p : Piece.values()) {
            // White pieces
            long whitePieces = board.getBitboard(p, true);
            while (whitePieces != 0) {
                int sq = Bits.lsb(whitePieces);
                mgScore += PieceSquareTables.mgValue(p, true, sq);
                egScore += PieceSquareTables.egValue(p, true, sq);
                whitePieces &= whitePieces - 1;
            }

            // Black pieces
            long blackPieces = board.getBitboard(p, false);
            while (blackPieces != 0) {
                int sq = Bits.lsb(blackPieces);
                mgScore -= PieceSquareTables.mgValue(p, false, sq);
                egScore -= PieceSquareTables.egValue(p, false, sq);
                blackPieces &= blackPieces - 1;
            }
        }

        // 2. Bishop Pair Bonus
        if (Long.bitCount(board.getBitboard(Piece.BISHOP, true)) >= 2) {
            mgScore += BISHOP_PAIR_BONUS;
            egScore += BISHOP_PAIR_BONUS;
        }
        if (Long.bitCount(board.getBitboard(Piece.BISHOP, false)) >= 2) {
            mgScore -= BISHOP_PAIR_BONUS;
            egScore -= BISHOP_PAIR_BONUS;
        }

        // 3. Simple Pawn Evaluation
        long whitePawns = board.getBitboard(Piece.PAWN, true);
        long blackPawns = board.getBitboard(Piece.PAWN, false);

        // Penalize doubled pawns (more than 1 pawn on the same file)
        int wDoubled = 0;
        int bDoubled = 0;
        for (int file = 0; file < 8; file++) {
            long fileMask = Bits.File.MASKS[file];
            int wCount = Long.bitCount(whitePawns & fileMask);
            int bCount = Long.bitCount(blackPawns & fileMask);
            if (wCount > 1) wDoubled += (wCount - 1);
            if (bCount > 1) bDoubled += (bCount - 1);
        }
        mgScore -= wDoubled * 15;
        egScore -= wDoubled * 20;
        mgScore += bDoubled * 15;
        egScore += bDoubled * 20;

        // 4. Tapered Phase Blending
        int phase = GamePhase.calculate(board);

        // phase ranges from 0 to 24.
        // Score is from White's perspective at this point.
        int score = (mgScore * phase + egScore * (GamePhase.MAX_PHASE - phase)) / GamePhase.MAX_PHASE;

        // 5. Return relative to the side to move (Negamax framework requires this)
        return board.isWhiteToMove() ? score : -score;
    }
}

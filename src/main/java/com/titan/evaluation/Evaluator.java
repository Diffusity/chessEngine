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

        // 3. Advanced Pawn Structure Evaluation
        long whitePawns = board.getBitboard(Piece.PAWN, true);
        long blackPawns = board.getBitboard(Piece.PAWN, false);

        int wDoubled = 0;
        int bDoubled = 0;
        int wIsolated = 0;
        int bIsolated = 0;
        int wPassedMG = 0;
        int wPassedEG = 0;
        int bPassedMG = 0;
        int bPassedEG = 0;

        // Passed pawn bonuses based on rank (from their side's perspective)
        // Ranks 1 to 8 (indices 0 to 7)
        int[] passedBonusMG = {0, 5, 10, 20, 35, 60, 100, 0};
        int[] passedBonusEG = {0, 10, 20, 40, 70, 120, 200, 0};

        // Evaluate White Pawns
        long wp = whitePawns;
        while (wp != 0) {
            int sq = Bits.lsb(wp);
            int file = sq & 7;
            int rank = sq >> 3;

            // Doubled
            if ((EvalBitboards.FRONT_SPAN_MASKS[1][sq] & whitePawns) != 0) {
                wDoubled++;
            }
            // Isolated
            if ((EvalBitboards.ADJACENT_FILE_MASKS[file] & whitePawns) == 0) {
                wIsolated++;
            }
            // Passed
            if ((EvalBitboards.PASSED_PAWN_MASKS[1][sq] & blackPawns) == 0) {
                wPassedMG += passedBonusMG[rank];
                wPassedEG += passedBonusEG[rank];
            }

            wp &= wp - 1;
        }

        // Evaluate Black Pawns
        long bp = blackPawns;
        while (bp != 0) {
            int sq = Bits.lsb(bp);
            int file = sq & 7;
            int rank = sq >> 3;

            // Doubled
            if ((EvalBitboards.FRONT_SPAN_MASKS[0][sq] & blackPawns) != 0) {
                bDoubled++;
            }
            // Isolated
            if ((EvalBitboards.ADJACENT_FILE_MASKS[file] & blackPawns) == 0) {
                bIsolated++;
            }
            // Passed
            if ((EvalBitboards.PASSED_PAWN_MASKS[0][sq] & whitePawns) == 0) {
                int relativeRank = 7 - rank;
                bPassedMG += passedBonusMG[relativeRank];
                bPassedEG += passedBonusEG[relativeRank];
            }

            bp &= bp - 1;
        }

        mgScore -= wDoubled * 15;
        egScore -= wDoubled * 20;
        mgScore -= wIsolated * 15;
        egScore -= wIsolated * 20;
        mgScore += wPassedMG;
        egScore += wPassedEG;

        mgScore += bDoubled * 15;
        egScore += bDoubled * 20;
        mgScore += bIsolated * 15;
        egScore += bIsolated * 20;
        mgScore -= bPassedMG;
        egScore -= bPassedEG;

        // 4. Tapered Phase Blending
        int phase = GamePhase.calculate(board);

        // phase ranges from 0 to 24.
        // Score is from White's perspective at this point.
        int score = (mgScore * phase + egScore * (GamePhase.MAX_PHASE - phase)) / GamePhase.MAX_PHASE;

        // 5. Return relative to the side to move (Negamax framework requires this)
        return board.isWhiteToMove() ? score : -score;
    }
}

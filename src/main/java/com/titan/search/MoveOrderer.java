package com.titan.search;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.board.Piece;
import com.titan.tables.history.HistoryTable;
import com.titan.tables.history.KillerTable;

import java.util.List;

/**
 * Scores and sorts moves for optimal alpha-beta pruning.
 */
public class MoveOrderer {

    // Score priority tiers
    static final int TT_MOVE_SCORE       = 10_000_000;
    static final int WINNING_CAPTURE     =  8_000_000;
    static final int QUEEN_PROMO         =  7_000_000;
    static final int KILLER_1_SCORE      =  6_000_000;
    static final int KILLER_2_SCORE      =  5_000_000;
    static final int LOSING_CAPTURE      = -2_000_000;

    /**
     * Scores all moves in the list.
     */
    public static void scoreMoves(
            List<Move> moves, int[] scores, Board board,
            int ttMove,
            KillerTable killers, int ply,
            HistoryTable history) {
        
        boolean white = board.isWhiteToMove();

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            int encoded = move.encoded();
            
            if (encoded == ttMove) {
                scores[i] = TT_MOVE_SCORE;
            } else if (move.isCapture()) {
                int score = mvvLva(board, move);
                // For now, assume all captures are "winning" or equal. 
                // A full SEE (Static Exchange Evaluation) is needed to classify losing captures.
                scores[i] = WINNING_CAPTURE + score;
            } else if (move.isPromotion() && move.promoPiece() == Piece.QUEEN) {
                scores[i] = QUEEN_PROMO;
            } else if (killers != null && killers.getFirst(ply) == encoded) {
                scores[i] = KILLER_1_SCORE;
            } else if (killers != null && killers.getSecond(ply) == encoded) {
                scores[i] = KILLER_2_SCORE;
            } else {
                if (history != null) {
                    scores[i] = history.getScore(white, move.from(), move.to());
                } else {
                    scores[i] = 0;
                }
            }
        }
    }

    /**
     * Selection sort for the next best move, starting from 'start'.
     */
    public static void pickMove(List<Move> moves, int[] scores, int start) {
        int bestIdx = start;
        int bestScore = scores[start];

        for (int i = start + 1; i < moves.size(); i++) {
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                bestIdx = i;
            }
        }

        if (bestIdx != start) {
            // Swap moves
            Move tempMove = moves.get(start);
            moves.set(start, moves.get(bestIdx));
            moves.set(bestIdx, tempMove);

            // Swap scores
            int tempScore = scores[start];
            scores[start] = scores[bestIdx];
            scores[bestIdx] = tempScore;
        }
    }

    /**
     * MVV-LVA: Most Valuable Victim - Least Valuable Attacker
     */
    private static int mvvLva(Board board, Move move) {
        Piece victim = board.pieceAt(move.to());
        Piece attacker = board.pieceAt(move.from());

        // En passant capture victim is a pawn
        if (move.isEnPassant()) {
            victim = Piece.PAWN;
        }

        if (victim == null || attacker == null) {
            return 0; 
        }

        // e.g. PxQ = 900 - (100 / 100) = 899
        return victim.value() - (attacker.value() / 100);
    }
}

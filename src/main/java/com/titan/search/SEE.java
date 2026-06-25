package com.titan.search;

import com.titan.board.Bits;
import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.board.Piece;
import com.titan.movegen.Attacks;

/**
 * Static Exchange Evaluation (SEE).
 * Determines whether a sequence of captures on a specific square is winning, losing, or neutral.
 */
public class SEE {

    private static final int[] PIECE_VALUES = {
        100, 320, 330, 500, 900, 20000
    };

    /**
     * Evaluates if a capture move gains at least 'threshold' material.
     */
    public static boolean see(Board board, Move move, int threshold) {
        int from = move.from();
        int to = move.to();
        
        int[] gain = new int[32];
        int d = 0;
        
        int victimVal = move.isEnPassant() ? PIECE_VALUES[Piece.PAWN.index()] : 
                        (board.pieceAt(to) != null ? PIECE_VALUES[board.pieceAt(to).index()] : 0);
                        
        if (move.isPromotion()) {
            victimVal += PIECE_VALUES[move.promoPiece().index()] - PIECE_VALUES[Piece.PAWN.index()];
        }
        
        gain[0] = victimVal;
        
        // Initial quick check if even winning the piece for free is worse than threshold
        if (gain[0] < threshold) return false;
        
        int attackerVal = PIECE_VALUES[board.pieceAt(from).index()];
        if (move.isPromotion()) {
            attackerVal = PIECE_VALUES[move.promoPiece().index()];
        }
        
        long occupied = board.getAllPieces();
        occupied = Bits.clearBit(occupied, from);
        occupied = Bits.setBit(occupied, to);
        
        boolean whiteToMove = !board.isWhiteToMove();
        long attackers = getAttackers(board, to, occupied);
        
        while (true) {
            d++;
            if (d >= 31) break; // prevent array out of bounds
            
            gain[d] = attackerVal - gain[d - 1];
            
            long myAttackers = attackers & board.getPieces(whiteToMove);
            if (myAttackers == 0) break;
            
            int lvaSq = getLeastValuableAttacker(board, myAttackers);
            Piece attacker = board.pieceAt(lvaSq);
            attackerVal = PIECE_VALUES[attacker.index()];
            
            occupied = Bits.clearBit(occupied, lvaSq);
            
            // X-ray attacks
            if (attacker == Piece.PAWN || attacker == Piece.BISHOP || attacker == Piece.ROOK || attacker == Piece.QUEEN) {
                attackers |= getAttackers(board, to, occupied) & ~occupied;
                attackers &= occupied;
            }
            
            whiteToMove = !whiteToMove;
        }
        
        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }
        
        return gain[0] >= threshold;
    }

    private static long getAttackers(Board board, int sq, long occupied) {
        long attackers = 0;
        
        // Pawns (to find white pawns attacking 'sq', we use black pawn attack pattern from 'sq', and vice versa)
        attackers |= Attacks.PAWN_ATTACKS[1][sq] & board.getBitboard(Piece.PAWN, true); // White pawns attacking 'sq'
        attackers |= Attacks.PAWN_ATTACKS[0][sq] & board.getBitboard(Piece.PAWN, false); // Black pawns attacking 'sq'
        
        // Knights
        attackers |= Attacks.KNIGHT_ATTACKS[sq] & (board.getBitboard(Piece.KNIGHT, true) | board.getBitboard(Piece.KNIGHT, false));
        
        // Bishops / Queens (Diagonal)
        long bishopsQueens = board.getBitboard(Piece.BISHOP, true) | board.getBitboard(Piece.BISHOP, false) |
                             board.getBitboard(Piece.QUEEN, true) | board.getBitboard(Piece.QUEEN, false);
        attackers |= Attacks.bishopAttacks(sq, occupied) & bishopsQueens;
        
        // Rooks / Queens (Orthogonal)
        long rooksQueens = board.getBitboard(Piece.ROOK, true) | board.getBitboard(Piece.ROOK, false) |
                           board.getBitboard(Piece.QUEEN, true) | board.getBitboard(Piece.QUEEN, false);
        attackers |= Attacks.rookAttacks(sq, occupied) & rooksQueens;
        
        // Kings
        attackers |= Attacks.KING_ATTACKS[sq] & (board.getBitboard(Piece.KING, true) | board.getBitboard(Piece.KING, false));
        
        return attackers & occupied;
    }

    private static int getLeastValuableAttacker(Board board, long attackers) {
        for (Piece piece : Piece.values()) {
            long subset = attackers & (board.getBitboard(piece, true) | board.getBitboard(piece, false));
            if (subset != 0) {
                return Bits.lsb(subset);
            }
        }
        return Bits.lsb(attackers); // Should never happen if attackers > 0
    }
}

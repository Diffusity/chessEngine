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
        
        int nextVictim = PIECE_VALUES[board.pieceAt(from).index()];
        
        int balance = move.isEnPassant() ? PIECE_VALUES[Piece.PAWN.index()] : 
                      (board.pieceAt(to) != null ? PIECE_VALUES[board.pieceAt(to).index()] : 0);
                      
        if (move.isPromotion()) {
            nextVictim = PIECE_VALUES[move.promoPiece().index()];
            balance += nextVictim - PIECE_VALUES[Piece.PAWN.index()];
        }
        
        balance -= threshold;
        if (balance < 0) return false;
        
        balance -= nextVictim;
        if (balance >= 0) return true;
        
        long occupied = board.getAllPieces();
        occupied = Bits.clearBit(occupied, from);
        occupied = Bits.setBit(occupied, to);
        
        boolean whiteToMove = !board.isWhiteToMove();
        
        long attackers = getAttackers(board, to, occupied);
        
        int[] gain = new int[32];
        gain[0] = balance + nextVictim;
        int d = 0;
        
        while (true) {
            long myAttackers = attackers & board.getPieces(whiteToMove);
            if (myAttackers == 0) break;
            
            int lvaSq = getLeastValuableAttacker(board, myAttackers);
            Piece attacker = board.pieceAt(lvaSq);
            
            occupied = Bits.clearBit(occupied, lvaSq);
            
            // X-ray attacks behind sliders
            if (attacker == Piece.PAWN || attacker == Piece.BISHOP || attacker == Piece.ROOK || attacker == Piece.QUEEN) {
                attackers |= getAttackers(board, to, occupied) & ~occupied; // add revealed attackers
                attackers &= occupied;
            }
            
            d++;
            gain[d] = nextVictim - gain[d - 1];
            nextVictim = PIECE_VALUES[attacker.index()];
            whiteToMove = !whiteToMove;
            
            if (Math.max(-gain[d-1], gain[d]) < 0) break;
        }
        
        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }
        
        return gain[0] >= 0;
    }

    private static long getAttackers(Board board, int sq, long occupied) {
        long attackers = 0;
        
        // Pawns
        attackers |= Attacks.PAWN_ATTACKS[0][sq] & board.getBitboard(Piece.PAWN, true); // White pawns attacking black sq
        attackers |= Attacks.PAWN_ATTACKS[1][sq] & board.getBitboard(Piece.PAWN, false); // Black pawns attacking white sq
        
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

package com.titan.movegen;

import com.titan.board.Bits;
import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.utils.FEN;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoveGeneratorTest {

    @Test
    @DisplayName("MoveGen - Starting position has 20 pseudo-legal and legal moves")
    void testStartingPosition() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        
        List<Move> pseudoLegal = MoveGenerator.generatePseudoLegalMoves(board);
        assertEquals(20, pseudoLegal.size());

        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        assertEquals(20, legal.size());
        
        // 16 pawn pushes (8 single, 8 double), 4 knight moves
    }

    @Test
    @DisplayName("MoveGen - White castling rights and legality")
    void testCastling() {
        // Position with clear kingside and queenside for white, but e1 is attacked by a black rook? 
        // No, let's just clear the path
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
        Board board = FEN.parse(fen);
        
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        
        boolean foundKingside = false;
        boolean foundQueenside = false;
        for (Move m : legal) {
            if (m.isCastling()) {
                if (m.to() == Bits.Square.G1) foundKingside = true;
                if (m.to() == Bits.Square.C1) foundQueenside = true;
            }
        }
        
        assertTrue(foundKingside, "Should be able to castle kingside");
        assertTrue(foundQueenside, "Should be able to castle queenside");
    }

    @Test
    @DisplayName("MoveGen - Cannot castle through check")
    void testCannotCastleThroughCheck() {
        // Black rook on f8 attacking f1. White king cannot castle kingside.
        String fen = "5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1";
        Board board = FEN.parse(fen);
        
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        
        boolean foundKingside = false;
        for (Move m : legal) {
            if (m.to() == Bits.Square.G1 && m.isCastling()) foundKingside = true;
        }
        
        assertFalse(foundKingside, "Should not be able to castle through check");
    }

    @Test
    @DisplayName("MoveGen - Pinned piece cannot move")
    void testAbsolutePin() {
        // White king on e1, White knight on e2, Black rook on e8. Knight is pinned.
        String fen = "4r3/8/8/8/8/8/4N3/4K3 w - - 0 1";
        Board board = FEN.parse(fen);
        
        List<Move> pseudoLegal = MoveGenerator.generatePseudoLegalMoves(board);
        boolean knightCanMovePseudo = false;
        for (Move m : pseudoLegal) {
            if (m.from() == Bits.Square.E2) knightCanMovePseudo = true;
        }
        assertTrue(knightCanMovePseudo, "Pinned piece can move pseudo-legally");
        
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        boolean knightCanMoveLegal = false;
        for (Move m : legal) {
            if (m.from() == Bits.Square.E2) knightCanMoveLegal = true;
        }
        assertFalse(knightCanMoveLegal, "Pinned piece cannot move legally");
    }

    @Test
    @DisplayName("MoveGen - Must resolve check")
    void testCheckEvasion() {
        // White in check from black rook on e8.
        String fen = "4r3/8/8/8/8/8/8/4K2R w K - 0 1";
        Board board = FEN.parse(fen);
        
        assertTrue(MoveGenerator.isInCheck(board, true));
        
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        
        // Legal moves: K -> d1, d2, f1, f2
        // Castling is NOT legal when in check
        assertEquals(4, legal.size());
        
        for (Move m : legal) {
            assertFalse(m.isCastling(), "Cannot castle to escape check");
            assertEquals(Bits.Square.E1, m.from(), "Only king can move");
        }
    }

    @Test
    @DisplayName("MoveGen - En passant")
    void testEnPassant() {
        // White pawn on d5, black pawn just moved e7-e5.
        String fen = "rnbqkbnr/pppp1ppp/8/3Pp3/8/8/PPP1PPPP/RNBQKBNR w KQkq e6 0 2";
        Board board = FEN.parse(fen);
        
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        
        boolean foundEp = false;
        for (Move m : legal) {
            if (m.isEnPassant()) {
                assertEquals(Bits.Square.D5, m.from());
                assertEquals(Bits.Square.E6, m.to());
                foundEp = true;
            }
        }
        
        assertTrue(foundEp, "En passant capture should be generated");
    }

    @Test
    @DisplayName("MoveGen - Perft Depth 1-3 for starting position")
    void testPerftStartingPosition() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        
        assertEquals(20, perft(board, 1));
        assertEquals(400, perft(board, 2));
        assertEquals(8902, perft(board, 3));
        // Depth 4 is 197281, Depth 5 is 4865609 (omitted for speed)
    }

    @Test
    @DisplayName("MoveGen - Perft Depth 1-3 for Kiwipete position")
    void testPerftKiwipete() {
        // Position 2 from https://www.chessprogramming.org/Perft_Results
        String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        Board board = FEN.parse(fen);
        
        assertEquals(48, perft(board, 1));
        assertEquals(2039, perft(board, 2));
        assertEquals(97862, perft(board, 3));
    }
    
    /**
     * Standard Perft (Performance Test) function.
     * Recursively counts the number of leaf nodes (possible positions) at a given depth.
     * This is the definitive way to prove a move generator is 100% bug-free.
     */
    private long perft(Board board, int depth) {
        if (depth == 0) return 1L;

        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        
        if (depth == 1) {
            return moves.size();
        }

        long nodes = 0;
        for (Move move : moves) {
            board.makeMove(move);
            nodes += perft(board, depth - 1);
            board.unmakeMove();
        }
        return nodes;
    }
}

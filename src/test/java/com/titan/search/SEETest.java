package com.titan.search;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.movegen.MoveGenerator;
import com.titan.utils.FEN;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SEETest {

    private Move findCapture(Board board, String algebraic) {
        List<Move> captures = MoveGenerator.generateCaptures(board);
        for (Move m : captures) {
            if (m.toUCI().equals(algebraic)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Capture move not found: " + algebraic);
    }

    @Test
    @DisplayName("SEE - Winning Capture (PxN)")
    void testWinningCapture() {
        // White pawn on d4 can capture black knight on e5
        String fen = "4k3/8/8/4n3/3P4/8/8/4K3 w - - 0 1";
        Board board = FEN.parse(fen);
        Move capture = findCapture(board, "d4e5");
        
        // Gains ~320 centipawns
        assertTrue(SEE.see(board, capture, 0));
        assertTrue(SEE.see(board, capture, 300));
        assertFalse(SEE.see(board, capture, 350));
    }

    @Test
    @DisplayName("SEE - Losing Capture (NxR defended by pawn)")
    void testLosingCapture() {
        // White knight on f3 captures black rook on d4, but d4 is defended by e5 pawn
        String fen = "4k3/8/8/4p3/3r4/5N2/8/4K3 w - - 0 1";
        Board board = FEN.parse(fen);
        Move capture = findCapture(board, "f3d4");
        
        // Gains 500 (Rook), but loses 320 (Knight) -> Net gain ~180
        assertTrue(SEE.see(board, capture, 0));
        assertTrue(SEE.see(board, capture, 100));
        assertFalse(SEE.see(board, capture, 200));
        
        // What if White rook on f4 captures black pawn on d4 defended by pawn?
        fen = "4k3/8/8/4p3/3p1R2/8/8/4K3 w - - 0 1";
        board = FEN.parse(fen);
        capture = findCapture(board, "f4d4");
        
        // Gains 100 (Pawn), but loses 500 (Rook) -> Net loss
        assertFalse(SEE.see(board, capture, 0));
    }

    @Test
    @DisplayName("SEE - Equal Exchange (RxR)")
    void testEqualExchange() {
        // White rook on h1 captures black rook on h8, defended by king on g8
        String fen = "6kr/8/8/8/8/8/8/4K2R w Kk - 0 1";
        // Let's set up a simpler trade: white rook h1 takes black rook h8, defended by king
        Board board = FEN.parse(fen);
        Move capture = findCapture(board, "h1h8");
        
        // Gains 500, loses 500 -> Net 0
        assertTrue(SEE.see(board, capture, 0));
        assertFalse(SEE.see(board, capture, 10));
    }
}

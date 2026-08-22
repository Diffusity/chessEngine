package com.titan.engine;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.search.SearchLimits;
import com.titan.utils.FEN;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    @Test
    @DisplayName("Engine - newGame sets board to startpos")
    void testNewGame() {
        Engine engine = new Engine();
        engine.newGame();
        
        assertEquals(FEN.STARTING_FEN, FEN.generate(engine.getBoard()));
    }

    @Test
    @DisplayName("Engine - setPosition with FEN")
    void testSetPositionFEN() {
        Engine engine = new Engine();
        String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        engine.setPosition(fen);
        
        assertEquals(fen, FEN.generate(engine.getBoard()));
    }

    @Test
    @DisplayName("Engine - applyMoves correctly parses and applies UCI strings")
    void testApplyMoves() {
        Engine engine = new Engine();
        engine.newGame();
        
        engine.applyMoves(List.of("e2e4", "e7e5"));

        // e6 is the nominal EP target, but no white pawn can capture there, so the
        // square is not recorded (conditional EP) and FEN prints '-'.
        String expectedFen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2";
        assertEquals(expectedFen, FEN.generate(engine.getBoard()));
    }

    @Test
    @DisplayName("Engine - applyMoves properly matches promotion flags")
    void testApplyMovesPromotion() {
        Engine engine = new Engine();
        engine.setPosition("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        
        engine.applyMoves(List.of("a7a8q")); // promotion to queen
        
        String expectedFen = "Q3k3/8/8/8/8/8/8/4K3 b - - 0 1";
        assertEquals(expectedFen, FEN.generate(engine.getBoard()));
    }

    @Test
    @DisplayName("Engine - applyMoves properly matches castling flags")
    void testApplyCastlingMove() {
        Engine engine = new Engine();
        engine.setPosition("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        
        engine.applyMoves(List.of("e1g1")); // white kingside castling
        
        String expectedFen = "r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1";
        assertEquals(expectedFen, FEN.generate(engine.getBoard()));
    }

    @Test
    @DisplayName("Engine - search can be started and stopped")
    void testSearchReturnsResult() throws InterruptedException {
        Engine engine = new Engine();
        engine.newGame();
        
        SearchLimits limits = SearchLimits.infinite();
        limits.init(engine.getBoard().isWhiteToMove());
        
        engine.startSearch(limits);
        
        // Wait a tiny bit for the thread to start searching
        Thread.sleep(100);
        
        engine.stopSearch(); // This should interrupt and join
        
        // If we reach here without hanging, the thread was successfully stopped
        assertTrue(true);
    }

    @Test
    @DisplayName("Engine - setHashSize resizes TranspositionTable")
    void testSetHashSize() {
        Engine engine = new Engine();
        engine.setHashSize(128); // 128 MB
        // Just verify it doesn't crash, the actual size is handled internally
        assertTrue(true);
    }
}

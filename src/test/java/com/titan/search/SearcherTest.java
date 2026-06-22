package com.titan.search;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.utils.FEN;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearcherTest {

    @Test
    @DisplayName("Search - Find Mate in 1")
    void testMateInOne() {
        // White to move, Qh5 can deliver Qxf7#
        String fen = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1";
        Board board = FEN.parse(fen);
        Searcher searcher = new Searcher(board);
        
        SearchLimits limits = SearchLimits.fixedDepth(2);
        limits.init(true);
        SearchResult result = searcher.search(board, limits);

        assertNotNull(result.bestMove(), "Should find a move");
        assertEquals("h5f7", result.bestMove().toUCI(), "Should find Qxf7 mate");
        assertTrue(Score.isMateScore(result.score()), "Score should be mate");
        assertTrue(result.score() > 0, "Mate score should be positive for white");
    }

    @Test
    @DisplayName("Search - Find Mate in 2")
    void testMateInTwo() {
        // White: Kg6, Rg1. Black: Kh8.
        // 1. Kf7 Kh7 2. Rh1#
        String fen = "7k/8/5K2/8/8/8/8/6R1 w - - 0 1";
        Board board = FEN.parse(fen);
        Searcher searcher = new Searcher(board);

        SearchLimits limits = SearchLimits.fixedDepth(4);
        limits.init(true);
        SearchResult result = searcher.search(board, limits);

        assertTrue(Score.isMateScore(result.score()), "Should find forced mate");
        // Score could be mate in 3 plies (1. Kf7 (ply 1) Kh7 (ply 2) 2. Rh1# (ply 3))
        assertEquals(Score.mateScore(3), result.score()); 
    }

    @Test
    @DisplayName("Search - Stalemate detection")
    void testStalemate() {
        // Black to move, and any King move would be illegal. Only stalemate if it's black's turn.
        // Wait, K vs KQ, White's turn, White can blunder into stalemate.
        // If White plays Qc7, it's stalemate. White should NOT play Qc7.
        String fen = "k7/8/1K6/2Q5/8/8/8/8 w - - 0 1";
        Board board = FEN.parse(fen);
        Searcher searcher = new Searcher(board);

        SearchLimits limits = SearchLimits.fixedDepth(3);
        limits.init(true);
        SearchResult result = searcher.search(board, limits);

        // Best move should NOT be Qc7
        assertNotEquals("c5c7", result.bestMove().toUCI(), "White should avoid stalemate move Qc7");
    }

    @Test
    @DisplayName("Search - Avoid blunder")
    void testAvoidBlunder() {
        // Black is attacking White's Queen on d4. White must move the Queen or lose it.
        // White to move.
        String fen = "rnbqkbnr/pppppppp/8/8/3Q4/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1";
        // Actually, wait, black pieces are on 7 and 8. No attack.
        // Let's set up a clear attack.
        String fenAttack = "r1bqkbnr/pppppppp/2n5/8/3Q4/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1"; // Nc6 attacks Qd4
        Board board = FEN.parse(fenAttack);
        Searcher searcher = new Searcher(board);

        SearchLimits limits = SearchLimits.fixedDepth(3);
        limits.init(true);
        SearchResult result = searcher.search(board, limits);

        assertNotNull(result.bestMove());
        // Move should be a queen move
        assertEquals(com.titan.board.Piece.QUEEN, board.pieceAt(result.bestMove().from()));
    }

    @Test
    @DisplayName("Search - Fixed depth search from start position")
    void testFixedDepthStartPos() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        Searcher searcher = new Searcher(board);

        SearchLimits limits = SearchLimits.fixedDepth(1);
        limits.init(true);
        SearchResult result = searcher.search(board, limits);

        assertNotNull(result.bestMove());
        assertTrue(result.depth() >= 1);
        assertTrue(result.nodes() > 0);
    }
    
    @Test
    @DisplayName("Search - Time-limited search completes")
    void testTimeLimitedSearch() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        Searcher searcher = new Searcher(board);

        // Give it 100ms
        SearchLimits limits = SearchLimits.moveTime(100);
        limits.init(true);
        SearchResult result = searcher.search(board, limits);

        assertNotNull(result.bestMove());
        assertTrue(result.timeMs() >= 0);
    }
}

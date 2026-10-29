package com.titan.search;

import com.titan.board.Board;
import com.titan.movegen.MoveGenerator;
import com.titan.utils.FEN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SearchFallbackTest {
    // Deterministic expiry: no sleeps, scheduler assumptions, or speed thresholds.
    private SearchLimits expiredLimits() {
        SearchLimits limits = new SearchLimits() {
            @Override public boolean isTimeUp() { return true; }
        };
        limits.init(true);
        return limits;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        FEN.STARTING_FEN,
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
        "7k/8/5K2/8/8/8/8/7R b - - 0 1",
        "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
    })
    void expiredBudgetStillReturnsLegalMoveWithoutClaimingCompletedDepth(String fen) {
        Board board = FEN.parse(fen);
        var legal = MoveGenerator.generateLegalMoves(board);
        String before = FEN.generate(board);
        long key = board.key();

        SearchResult result = new Searcher(board).search(board, expiredLimits());

        assertNotNull(result.bestMove(), "A timeout must not turn a playable position into bestmove 0000");
        assertTrue(legal.stream().anyMatch(m -> m.encoded() == result.bestMove().encoded()));
        assertEquals(0, result.depth(), "No iteration completed");
        assertEquals(0, result.nodes());
        assertTrue(result.pv().isEmpty(), "An unsearched fallback is not an analysed PV");
        assertEquals(before, FEN.generate(board));
        assertEquals(key, board.key());
    }

    @Test
    void expiredBudgetStillRecognisesRootCheckmate() {
        Board board = FEN.parse("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1");
        SearchResult result = new Searcher(board).search(board, expiredLimits());
        assertNull(result.bestMove());
        assertEquals(-Score.mateScore(0), result.score());
        assertEquals(0, result.depth());
        assertTrue(result.pv().isEmpty());
    }

    @Test
    void expiredBudgetStillRecognisesRootStalemate() {
        Board board = FEN.parse("7k/5Q2/5K2/8/8/8/8/8 b - - 0 1");
        SearchResult result = new Searcher(board).search(board, expiredLimits());
        assertNull(result.bestMove());
        assertEquals(Score.DRAW, result.score());
        assertEquals(0, result.depth());
    }

    @Test
    void interruptionDuringNextIterationPreservesCompletedResultAndPv() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        AtomicInteger completedDepth = new AtomicInteger();
        SearchLimits interrupted = new SearchLimits() {
            private int checks;
            @Override public boolean isTimeUp() { return ++checks > 1; }
            @Override public boolean shouldDeepen() {
                completedDepth.incrementAndGet();
                return true;
            }
        };
        interrupted.depth = 8;
        interrupted.init(true);
        SearchResult result = new Searcher(board).search(board, interrupted);

        assertTrue(completedDepth.get() > 0 && completedDepth.get() < 8);
        Board referenceBoard = FEN.parse(FEN.STARTING_FEN);
        SearchLimits referenceLimits = SearchLimits.fixedDepth(completedDepth.get());
        referenceLimits.init(true);
        SearchResult reference = new Searcher(referenceBoard).search(referenceBoard, referenceLimits);

        assertEquals(reference.depth(), result.depth());
        assertEquals(reference.bestMove().toUCI(), result.bestMove().toUCI());
        assertEquals(reference.score(), result.score());
        assertEquals(reference.pv(), result.pv(), "The unfinished iteration must not replace the completed PV");
        assertEquals(FEN.STARTING_FEN, FEN.generate(board));
    }

    @Test
    void successfulSearchAfterTimeoutDoesNotReuseFallbackAsItsResult() {
        Board board = FEN.parse("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1");
        Searcher searcher = new Searcher(board);
        searcher.search(board, expiredLimits());
        SearchLimits normal = SearchLimits.fixedDepth(2);
        normal.init(true);
        SearchResult result = searcher.search(board, normal);
        assertEquals("h5f7", result.bestMove().toUCI());
        assertTrue(Score.isMateScore(result.score()));
        assertTrue(result.depth() > 0);
    }
}

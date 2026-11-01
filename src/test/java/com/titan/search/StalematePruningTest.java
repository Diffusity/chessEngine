package com.titan.search;

import com.titan.board.Board;
import com.titan.evaluation.Evaluator;
import com.titan.movegen.MoveGenerator;
import com.titan.utils.FEN;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class StalematePruningTest {
    @Test void stalemateRemainsDrawWhenStaticEvaluationWouldTriggerPruning() throws Exception {
        for (int depth = 1; depth <= 3; depth++) {
            assertStalemate("7k/5Q2/5K2/8/8/8/8/8 b - - 0 1", depth);
        }
    }

    @Test void trappedNonPawnMaterialDoesNotPermitNullMoveInStalemate() throws Exception {
        String fen = "k6n/2Q2p2/2K2Pp1/6P1/8/8/8/8 b - - 0 1";
        assertTrue(FEN.parse(fen).hasNonPawnMaterial(false));
        assertStalemate(fen, 4);
    }

    private void assertStalemate(String fen, int depth) throws Exception {
        Board board = FEN.parse(fen);
        assertFalse(MoveGenerator.isInCheck(board, false));
        assertTrue(MoveGenerator.generateLegalMoves(board).isEmpty());
        String before = FEN.generate(board);
        long key = board.key();
        int beta = Evaluator.evaluate(board) - 1000;
        Method search = Searcher.class.getDeclaredMethod("alphaBeta",
                int.class, int.class, int.class, int.class, boolean.class);
        search.setAccessible(true);
        Field limitsField = Searcher.class.getDeclaredField("limits");
        limitsField.setAccessible(true);
        Searcher searcher = new Searcher(board);
        SearchLimits limits = SearchLimits.infinite();
        limits.init(false);
        limitsField.set(searcher, limits);
        int score = (int) search.invoke(searcher, depth, 1, beta - 1, beta, true);
        assertEquals(Score.DRAW, score, "Terminal draw must precede static pruning at depth " + depth);
        assertEquals(before, FEN.generate(board));
        assertEquals(key, board.key());
    }
}

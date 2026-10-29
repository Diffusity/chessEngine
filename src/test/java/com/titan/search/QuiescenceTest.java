package com.titan.search;

import com.titan.board.Board;
import com.titan.evaluation.Evaluator;
import com.titan.movegen.MoveGenerator;
import com.titan.utils.FEN;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class QuiescenceTest {
    private int evaluate(Board board) throws Exception {
        String before = FEN.generate(board);
        long key = board.key();
        Searcher searcher = new Searcher(board);
        SearchLimits limits = SearchLimits.infinite();
        limits.init(board.isWhiteToMove());
        Field field = Searcher.class.getDeclaredField("limits");
        field.setAccessible(true);
        field.set(searcher, limits);
        Method method = Searcher.class.getDeclaredMethod("quiescence", int.class, int.class, int.class);
        method.setAccessible(true);
        int score = (int) method.invoke(searcher, -Score.INFINITY, Score.INFINITY, 1);
        assertEquals(before, FEN.generate(board));
        assertEquals(key, board.key());
        return score;
    }

    @Test void checkmateAtQuiescenceNodeIsMate() throws Exception {
        assertEquals(-Score.mateScore(1), evaluate(FEN.parse("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1")));
    }

    @Test void stalemateAtQuiescenceNodeIsDraw() throws Exception {
        assertEquals(Score.DRAW, evaluate(FEN.parse("7k/5Q2/5K2/8/8/8/8/8 b - - 0 1")));
    }

    @Test void quietCheckEvasionIsSearchedInsteadOfStandPat() throws Exception {
        Board board = FEN.parse("7k/8/5K2/8/8/8/8/7R b - - 0 1");
        var legal = MoveGenerator.generateLegalMoves(board);
        assertEquals(1, legal.size());
        assertEquals("h8g8", legal.get(0).toUCI());
        board.makeMove(legal.get(0));
        int expected = -Evaluator.evaluate(board);
        board.unmakeMove();
        assertEquals(expected, evaluate(board));
    }

    @Test void quietPromotionIsNotOmittedAtHorizon() throws Exception {
        assertTrue(evaluate(FEN.parse("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")) > 500);
    }
}

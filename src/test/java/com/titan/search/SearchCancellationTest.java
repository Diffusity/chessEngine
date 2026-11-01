package com.titan.search;

import com.titan.board.Board;
import com.titan.movegen.MoveGenerator;
import com.titan.utils.FEN;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SearchCancellationTest {
    @Test void cancellationBeforeWorkerEntersSearchIsNotLost() throws Exception {
        Board board = FEN.parse(FEN.STARTING_FEN);
        Searcher searcher = new Searcher(board);
        SearchLimits limits = SearchLimits.fixedDepth(3);
        limits.init(true);
        AtomicReference<SearchResult> answer = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            answer.set(searcher.search(board, limits));
        });
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        SearchResult result = answer.get();
        assertNotNull(result);
        assertNotNull(result.bestMove());
        assertTrue(MoveGenerator.generateLegalMoves(board).contains(result.bestMove()));
        assertEquals(0, result.depth(), "Cancellation must survive search initialization");
        assertEquals(0, result.nodes());
    }
}

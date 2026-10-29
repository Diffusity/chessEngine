package com.titan.engine;

import com.titan.search.SearchLimits;
import com.titan.utils.FEN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class EngineLifecycleTest {
    // Hold the worker before Searcher.search can reset its stop flag.
    private static class PausedSearch implements AutoCloseable {
        final CountDownLatch waiting = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Thread> worker = new AtomicReference<>();
        final Engine engine = new Engine(task -> {
            Thread thread = new Thread(() -> {
                waiting.countDown();
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                task.run();
            });
            worker.set(thread);
            return thread;
        });
        PausedSearch() throws InterruptedException {
            SearchLimits limits = SearchLimits.fixedDepth(1);
            limits.init(true);
            engine.startSearch(limits);
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
        }
        public void close() throws InterruptedException {
            release.countDown();
            engine.stopSearch();
            worker.get().join(5000);
        }
    }

    @Test void stopBeforeSearchStartsJoinsWorker() throws Exception {
        try (PausedSearch search = new PausedSearch()) {
            search.engine.stopSearch();
            assertFalse(search.worker.get().isAlive(), "stop must not leave its worker behind");
            assertEquals(FEN.STARTING_FEN, FEN.generate(search.engine.getBoard()));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"position", "newgame", "moves", "hash"})
    void mutationsJoinPreviousWorker(String mutation) throws Exception {
        try (PausedSearch search = new PausedSearch()) {
            switch (mutation) {
                case "position" -> search.engine.setPosition("7k/8/5K2/8/8/8/8/6R1 w - - 0 1");
                case "newgame" -> search.engine.newGame();
                case "moves" -> search.engine.applyMoves(List.of("e2e4"));
                case "hash" -> search.engine.setHashSize(1);
            }
            assertFalse(search.worker.get().isAlive(), "shared state cannot change while the previous worker lives");
        }
    }
}

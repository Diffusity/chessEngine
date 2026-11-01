package com.titan.search;

import com.titan.uci.UCICommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SearchLimitsTest {
    @Test void monotonicClockTriggersExactHardBoundaryWithoutSleeping() {
        AtomicLong clock = new AtomicLong(7_000_000_000L);
        SearchLimits limits = new SearchLimits(clock::get);
        limits.movetime = 100;
        limits.init(true);
        clock.addAndGet(99_000_000L);
        assertEquals(99, limits.elapsedMillis());
        assertFalse(limits.isTimeUp());
        clock.addAndGet(1_000_000L);
        assertTrue(limits.isTimeUp());
        assertFalse(limits.shouldDeepen());
    }

    @Test void explicitMoveTimeIsCappedByRemainingClock() {
        SearchLimits limits = parse("go movetime 5000 wtime 100 btime 100");
        limits.init(true);
        assertTrue(limits.hardLimit < 100);
    }

    private SearchLimits parse(String command) {
        return ((UCICommand.Go) UCICommand.parse(command)).limits();
    }

    @ParameterizedTest @ValueSource(strings = {
        "go wtime 0 btime 0", "go wtime 0 btime 1000 winc 2000", "go movetime 0"
    })
    void explicitlyExhaustedBudgetExpiresImmediately(String command) {
        SearchLimits limits = parse(command);
        limits.init(true);
        assertEquals(0, limits.hardLimit);
        assertEquals(0, limits.softLimit);
        assertTrue(limits.isTimeUp());
        assertFalse(limits.shouldDeepen());
    }

    @ParameterizedTest @ValueSource(longs = {1, 5, 10, 20, 100, 1000, 180000})
    void incrementCannotBeSpentBeforeMoveAndBudgetsStayOrdered(long remaining) {
        SearchLimits limits = parse("go wtime " + remaining + " btime 180000 winc 2000 binc 2000");
        limits.init(true);
        assertTrue(limits.hardLimit >= 0 && limits.hardLimit <= remaining);
        assertTrue(limits.softLimit >= 0 && limits.softLimit <= limits.hardLimit);
    }

    @Test void blackUsesItsOwnClock() {
        SearchLimits limits = parse("go wtime 180000 btime 1000 winc 2000 binc 0");
        limits.init(false);
        assertTrue(limits.hardLimit <= 1000);
        assertTrue(limits.softLimit <= limits.hardLimit);
    }

    @Test void fixedDepthAndUnspecifiedClockAreNotAccidentalTimeouts() {
        for (String command : new String[]{"go depth 3", "go", "go infinite"}) {
            SearchLimits limits = parse(command);
            limits.init(true);
            assertFalse(limits.isTimeUp());
            assertTrue(limits.shouldDeepen());
        }
    }

    @Test void explicitZeroClockStillAppliesWithDepthLimit() {
        SearchLimits limits = parse("go depth 8 wtime 0 btime 0");
        limits.init(true);
        assertTrue(limits.isTimeUp());
    }
}

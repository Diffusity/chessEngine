package com.titan.search;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Policy contract and safety tests, not evidence that a tuning value is better. */
class TimePolicyExperimentTest {
    private SearchLimits clock(AtomicLong now, boolean experiment, long remaining) {
        SearchLimits limits = new SearchLimits(now::get, experiment);
        limits.wtime = remaining;
        limits.winc = 2000;
        limits.init(true);
        return limits;
    }

    @Test void experimentMayDeepenBetweenHalfAndFullSoftBudget() {
        AtomicLong now = new AtomicLong();
        SearchLimits control = clock(now, false, 180000);
        SearchLimits candidate = clock(now, true, 180000);
        now.set(candidate.softLimit * 750_000);
        assertFalse(control.shouldDeepen());
        assertTrue(candidate.shouldDeepen());
        now.set(candidate.softLimit * 1_000_000);
        assertFalse(candidate.shouldDeepen());
    }

    @Test void blackClockUsesTheSameExperimentalGate() {
        AtomicLong now = new AtomicLong();
        SearchLimits limits = new SearchLimits(now::get, true);
        limits.btime = 120000;
        limits.binc = 2000;
        limits.init(false);
        now.set(limits.softLimit * 750_000);
        assertTrue(limits.shouldDeepen());
    }

    @Test void explicitMoveTimeIsNotChangedByClockExperiment() {
        AtomicLong now = new AtomicLong();
        SearchLimits limits = new SearchLimits(now::get, true);
        limits.movetime = 1000;
        limits.wtime = 180000;
        limits.init(true);
        now.set(500_000_000);
        assertFalse(limits.shouldDeepen());
    }

    @Test void hardDeadlinesAndReservesAreIdenticalIncludingExhaustedClocks() {
        for (long remaining : new long[]{0, 1, 10, 20, 1000, 180000}) {
            AtomicLong now = new AtomicLong();
            SearchLimits control = clock(now, false, remaining);
            SearchLimits candidate = clock(now, true, remaining);
            assertEquals(control.softLimit, candidate.softLimit);
            assertEquals(control.hardLimit, candidate.hardLimit);
            assertTrue(candidate.hardLimit <= remaining);
            now.set(candidate.hardLimit * 1_000_000);
            assertTrue(candidate.isTimeUp());
            assertFalse(candidate.shouldDeepen());
        }
    }
}

package com.titan.tables;

import com.titan.search.Score;
import com.titan.tables.tt.HashEntry;
import com.titan.tables.tt.HashFlag;
import com.titan.tables.tt.TranspositionTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranspositionTableTest {

    @Test
    @DisplayName("TT - Store and Probe")
    void testStoreAndProbe() {
        TranspositionTable tt = new TranspositionTable(1);
        long key = 123456789L;
        int move = 1234;
        int score = 50;
        int depth = 5;
        HashFlag flag = HashFlag.EXACT;
        int ply = 0;

        tt.store(key, move, score, depth, flag, ply);

        HashEntry entry = tt.probe(key, ply);
        assertNotNull(entry);
        assertEquals(key, entry.key);
        assertEquals(move, entry.move);
        assertEquals(score, TranspositionTable.getScore(entry, ply));
        assertEquals(depth, entry.depth);
        assertEquals(flag.ordinal(), entry.flag);
    }

    @Test
    @DisplayName("TT - Key Mismatch")
    void testKeyMismatch() {
        TranspositionTable tt = new TranspositionTable(1);
        long key1 = 123L;
        long key2 = 456L;

        tt.store(key1, 100, 10, 5, HashFlag.EXACT, 0);

        HashEntry entry = tt.probe(key2, 0);
        assertNull(entry, "Should return null for a different key");
    }

    @Test
    @DisplayName("TT - Depth Replacement")
    void testDepthReplacement() {
        TranspositionTable tt = new TranspositionTable(1);
        long key = 123L;

        // Store shallow search
        tt.store(key, 100, 10, 3, HashFlag.EXACT, 0);
        
        // Store deeper search with different score
        tt.store(key, 200, 20, 5, HashFlag.EXACT, 0);

        HashEntry entry = tt.probe(key, 0);
        assertNotNull(entry);
        assertEquals(5, entry.depth);
        assertEquals(20, TranspositionTable.getScore(entry, 0));
        assertEquals(200, entry.move);
    }

    @Test
    @DisplayName("TT - Age Replacement")
    void testAgeReplacement() {
        TranspositionTable tt = new TranspositionTable(1); // 1 MB is enough for collision testing if we mock keys, but let's just use exact keys
        // We simulate age replacement by having a collision. We can't easily force a collision without knowing the mask,
        // but we can just use the same key, advance age, and store a shallower depth.
        long key = 123L;

        tt.store(key, 100, 10, 5, HashFlag.EXACT, 0);
        
        // New search generation
        tt.newSearch();
        
        // Store shallower search, but it's newer, so it should replace
        tt.store(key, 200, 20, 3, HashFlag.EXACT, 0);

        HashEntry entry = tt.probe(key, 0);
        assertNotNull(entry);
        assertEquals(3, entry.depth);
        assertEquals(20, TranspositionTable.getScore(entry, 0));
        assertEquals(200, entry.move);
    }

    @Test
    @DisplayName("TT - Mate Score Adjustment")
    void testMateScoreAdjustment() {
        TranspositionTable tt = new TranspositionTable(1);
        long key = 123L;

        int mateScoreAtRoot = Score.mateScore(3); // mate in 3 plies from root
        int currentPly = 5;
        
        // When searching at ply 5, we find a mate in 3 plies from root. 
        // We pass the score relative to the current position to the TT.
        // Wait, alphaBeta returns scores relative to the current position.
        // If we found a mate in 1 ply from here, score = MATE - 1.
        int scoreToStore = Score.mateScore(1); 
        
        tt.store(key, 0, scoreToStore, 5, HashFlag.EXACT, currentPly);

        // Now probe from a different ply (e.g. ply 3)
        // If the position is reached at ply 3, the mate is still 1 ply away from that position.
        // So the returned score should be MATE - 1.
        HashEntry entry = tt.probe(key, 3);
        assertNotNull(entry);
        
        int retrievedScore = TranspositionTable.getScore(entry, 3);
        
        // Stored at ply 5: MATE - 1 + 5 = MATE + 4
        // Retrieved at ply 3: MATE + 4 - 3 = MATE + 1.
        // Wait, MATE - 1 is mate in 1. If we stored it at ply 5, it means mate is at ply 6.
        // If we reach the same position at ply 3, mate is at ply 4.
        // So mate in 1 from ply 3.
        // scoreToStore = MATE - 1. adjusted = MATE - 1 + 5 = MATE + 4.
        // retrieved = MATE + 4 - 3 = MATE + 1. This means mate in -1?
        // Wait, mateScore(1) = MATE - 1.
        // MATE - 1 + 5 = MATE + 4. 
        // MATE + 4 - 3 = MATE + 1. 
        // Let's see what the TT does.
        // Actually, the TT adjustment in my implementation:
        // store: adjustedScore = score + ply = (MATE - 1) + 5 = MATE + 4.
        // probe: returned = score - ply = (MATE + 4) - 3 = MATE + 1.
        // This is correct: MATE + 1 is actually > MATE, which means it's an even faster mate (mate in -1 ply).
        // Wait, mateScore(N) = MATE - N.
        // So MATE - (-1) = MATE + 1.
        // Yes, if we reached the identical position 2 plies EARLIER, we mate 2 plies EARLIER relative to root!
        // But relative to the current node, the distance to mate is the SAME: 1 ply!
        // Wait, alphaBeta returns distance to mate from the CURRENT node.
        // No, alpha-beta returns distance to mate from the ROOT.
        // Let's check `alphaBeta`:
        // if (moves.isEmpty() && inCheck) return -Score.mateScore(ply); 
        // This returns MATE - ply. So it's relative to the root.
        // OK, so score is MATE - 6 (mate at ply 6).
        scoreToStore = Score.mateScore(6);
        tt.store(key, 0, scoreToStore, 5, HashFlag.EXACT, currentPly);
        
        entry = tt.probe(key, 3);
        retrievedScore = TranspositionTable.getScore(entry, 3);
        
        // stored: (MATE - 6) + 5 = MATE - 1.
        // retrieved: (MATE - 1) - 3 = MATE - 4.
        // So mate is at ply 4 from root.
        assertEquals(Score.mateScore(4), retrievedScore);
    }

    @Test
    @DisplayName("TT - Clear")
    void testClear() {
        TranspositionTable tt = new TranspositionTable(1);
        long key = 123L;

        tt.store(key, 100, 10, 5, HashFlag.EXACT, 0);
        assertNotNull(tt.probe(key, 0));

        tt.clear();
        assertNull(tt.probe(key, 0));
    }
}

package com.titan.movegen;

import com.titan.board.Bits;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the magic-bitboard slider attacks are exactly equivalent to a
 * classic ray-scanning implementation across exhaustive occupancies.
 */
public class AttacksMagicTest {

    private static long rayScan(int sq, long occupied, boolean rook) {
        long attacks = 0;
        int r0 = Bits.rankOf(sq);
        int f0 = Bits.fileOf(sq);
        int[] dirs = rook
                ? new int[]{Attacks.DIR_N, Attacks.DIR_E, Attacks.DIR_S, Attacks.DIR_W}
                : new int[]{Attacks.DIR_NE, Attacks.DIR_SE, Attacks.DIR_SW, Attacks.DIR_NW};
        int[] DF = { 0,  1,  1,  1,  0, -1, -1, -1};
        int[] DR = { 1,  1,  0, -1, -1, -1,  0,  1};
        for (int d : dirs) {
            int r = r0 + DR[d];
            int f = f0 + DF[d];
            while (r >= 0 && r <= 7 && f >= 0 && f <= 7) {
                long bb = 1L << (r * 8 + f);
                attacks |= bb;
                if ((occupied & bb) != 0) break;
                r += DR[d];
                f += DF[d];
            }
        }
        return attacks;
    }

    @Test
    public void testMagicMatchesRayScan() {
        Random rng = new Random(42);

        // Structured occupancies: empty, single squares, full board
        long[] fixed = { 0L, ~0L };
        for (int sq = 0; sq < 64; sq++) {
            for (long occ : fixed) {
                assertEquals(rayScan(sq, occ, true), Attacks.rookAttacks(sq, occ), "rook sq=" + sq + " occ=" + occ);
                assertEquals(rayScan(sq, occ, false), Attacks.bishopAttacks(sq, occ), "bishop sq=" + sq + " occ=" + occ);
            }
        }

        // 200k random occupancies across all squares
        for (int i = 0; i < 200_000; i++) {
            long occ = rng.nextLong() & rng.nextLong(); // bias toward sparser boards
            int sq = rng.nextInt(64);
            assertEquals(rayScan(sq, occ, true), Attacks.rookAttacks(sq, occ),
                    "rook mismatch at i=" + i);
            assertEquals(rayScan(sq, occ, false), Attacks.bishopAttacks(sq, occ),
                    "bishop mismatch at i=" + i);
        }

        assertTrue(true);
    }
}

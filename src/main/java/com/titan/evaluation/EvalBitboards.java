package com.titan.evaluation;

import com.titan.board.Bits;

/**
 * Pre-computed bitboards used specifically for evaluation.
 * Calculates pawn spans, adjacent files, and passed pawn masks.
 */
public class EvalBitboards {

    /** Masks for the files adjacent to a given file (0-7). */
    public static final long[] ADJACENT_FILE_MASKS = new long[8];

    /** Masks for all squares directly ahead on the same file. Index: [color][square] */
    public static final long[][] FRONT_SPAN_MASKS = new long[2][64];

    /** Masks for all squares ahead on the same file AND adjacent files. Index: [color][square] */
    public static final long[][] PASSED_PAWN_MASKS = new long[2][64];

    static {
        // Initialize adjacent file masks
        for (int f = 0; f < 8; f++) {
            long mask = 0L;
            if (f > 0) mask |= Bits.File.MASKS[f - 1];
            if (f < 7) mask |= Bits.File.MASKS[f + 1];
            ADJACENT_FILE_MASKS[f] = mask;
        }

        // Initialize front span and passed pawn masks
        for (int sq = 0; sq < 64; sq++) {
            int file = sq & 7;
            int rank = sq >> 3;

            long wFront = 0L;
            long bFront = 0L;

            // Squares ahead for white
            for (int r = rank + 1; r < 8; r++) {
                wFront |= Bits.toBitboard(Bits.toSquare(file, r));
            }

            // Squares ahead for black
            for (int r = rank - 1; r >= 0; r--) {
                bFront |= Bits.toBitboard(Bits.toSquare(file, r));
            }

            FRONT_SPAN_MASKS[1][sq] = wFront; // White is index 1
            FRONT_SPAN_MASKS[0][sq] = bFront; // Black is index 0

            long wAdjacent = 0L;
            long bAdjacent = 0L;
            if (file > 0) {
                for (int r = rank + 1; r < 8; r++) wAdjacent |= Bits.toBitboard(Bits.toSquare(file - 1, r));
                for (int r = rank - 1; r >= 0; r--) bAdjacent |= Bits.toBitboard(Bits.toSquare(file - 1, r));
            }
            if (file < 7) {
                for (int r = rank + 1; r < 8; r++) wAdjacent |= Bits.toBitboard(Bits.toSquare(file + 1, r));
                for (int r = rank - 1; r >= 0; r--) bAdjacent |= Bits.toBitboard(Bits.toSquare(file + 1, r));
            }

            PASSED_PAWN_MASKS[1][sq] = wFront | wAdjacent;
            PASSED_PAWN_MASKS[0][sq] = bFront | bAdjacent;
        }
    }
}

package com.titan.board;

/**
 * Bitboard utility constants and helper methods.
 * <p>
 * A bitboard is a 64-bit long where each bit represents a square on the chess board.
 * Bit 0 = a1, Bit 1 = b1, ..., Bit 7 = h1, Bit 8 = a2, ..., Bit 63 = h8.
 * <p>
 * This class provides:
 * <ul>
 *   <li>File and rank bitmasks for filtering squares by column/row</li>
 *   <li>Square index constants (a1=0 through h8=63)</li>
 *   <li>Bit manipulation primitives used throughout the engine</li>
 * </ul>
 *
 * @see <a href="https://www.chessprogramming.org/Bitboards">Chess Programming Wiki — Bitboards</a>
 */
public final class Bits {

    private Bits() {
        // Utility class — prevent instantiation
    }

    // ========================
    // File Masks (columns a-h)
    // ========================

    /** Bitmask constants for each file (vertical column) of the board. */
    public static final class File {
        public static final long A = 0x0101010101010101L;
        public static final long B = 0x0202020202020202L;
        public static final long C = 0x0404040404040404L;
        public static final long D = 0x0808080808080808L;
        public static final long E = 0x1010101010101010L;
        public static final long F = 0x2020202020202020L;
        public static final long G = 0x4040404040404040L;
        public static final long H = 0x8080808080808080L;

        /** Array of file masks indexed by file number (0=A, 7=H). */
        public static final long[] MASKS = { A, B, C, D, E, F, G, H };

        private File() {}
    }

    // ========================
    // Rank Masks (rows 1-8)
    // ========================

    /** Bitmask constants for each rank (horizontal row) of the board. */
    public static final class Rank {
        public static final long _1 = 0x00000000000000FFL;
        public static final long _2 = 0x000000000000FF00L;
        public static final long _3 = 0x0000000000FF0000L;
        public static final long _4 = 0x00000000FF000000L;
        public static final long _5 = 0x000000FF00000000L;
        public static final long _6 = 0x0000FF0000000000L;
        public static final long _7 = 0x00FF000000000000L;
        public static final long _8 = 0xFF00000000000000L;

        /** Array of rank masks indexed by rank number (0=rank 1, 7=rank 8). */
        public static final long[] MASKS = { _1, _2, _3, _4, _5, _6, _7, _8 };

        /** Promotion rank for white (rank 8). */
        public static final long WHITE_PROMOTION = _8;
        /** Promotion rank for black (rank 1). */
        public static final long BLACK_PROMOTION = _1;
        /** Pawn start rank for white (rank 2). */
        public static final long WHITE_PAWN_START = _2;
        /** Pawn start rank for black (rank 7). */
        public static final long BLACK_PAWN_START = _7;

        private Rank() {}
    }

    // ========================
    // Square Constants
    // ========================

    /**
     * Named constants for all 64 squares.
     * Square index = rank * 8 + file, where a1=0, h8=63 (Little-Endian Rank-File Mapping).
     */
    public static final class Square {
        public static final int COUNT = 64;

        // @formatter:off
        public static final int A1 = 0,  B1 = 1,  C1 = 2,  D1 = 3,  E1 = 4,  F1 = 5,  G1 = 6,  H1 = 7;
        public static final int A2 = 8,  B2 = 9,  C2 = 10, D2 = 11, E2 = 12, F2 = 13, G2 = 14, H2 = 15;
        public static final int A3 = 16, B3 = 17, C3 = 18, D3 = 19, E3 = 20, F3 = 21, G3 = 22, H3 = 23;
        public static final int A4 = 24, B4 = 25, C4 = 26, D4 = 27, E4 = 28, F4 = 29, G4 = 30, H4 = 31;
        public static final int A5 = 32, B5 = 33, C5 = 34, D5 = 35, E5 = 36, F5 = 37, G5 = 38, H5 = 39;
        public static final int A6 = 40, B6 = 41, C6 = 42, D6 = 43, E6 = 44, F6 = 45, G6 = 46, H6 = 47;
        public static final int A7 = 48, B7 = 49, C7 = 50, D7 = 51, E7 = 52, F7 = 53, G7 = 54, H7 = 55;
        public static final int A8 = 56, B8 = 57, C8 = 58, D8 = 59, E8 = 60, F8 = 61, G8 = 62, H8 = 63;
        // @formatter:on

        /** No square / invalid square sentinel value. */
        public static final int NONE = -1;

        private Square() {}
    }

    // ========================
    // Bit Manipulation Helpers
    // ========================

    /**
     * Returns a bitboard with only the given square set.
     *
     * @param square the square index (0-63)
     * @return a long with bit at 'square' set to 1
     */
    public static long toBitboard(int square) {
        return 1L << square;
    }

    /**
     * Sets the bit at the given square in the bitboard.
     *
     * @param bitboard the original bitboard
     * @param square   the square index to set (0-63)
     * @return the bitboard with the specified bit set
     */
    public static long setBit(long bitboard, int square) {
        return bitboard | (1L << square);
    }

    /**
     * Clears the bit at the given square in the bitboard.
     *
     * @param bitboard the original bitboard
     * @param square   the square index to clear (0-63)
     * @return the bitboard with the specified bit cleared
     */
    public static long clearBit(long bitboard, int square) {
        return bitboard & ~(1L << square);
    }

    /**
     * Checks whether the bit at the given square is set.
     *
     * @param bitboard the bitboard to test
     * @param square   the square index to check (0-63)
     * @return true if the bit at 'square' is set
     */
    public static boolean hasBit(long bitboard, int square) {
        return (bitboard & (1L << square)) != 0;
    }

    /**
     * Returns the index of the least significant bit (LSB) that is set.
     * Equivalent to finding the "first" square in a bitboard.
     *
     * @param bitboard a non-zero bitboard
     * @return the index (0-63) of the LSB
     */
    public static int lsb(long bitboard) {
        return Long.numberOfTrailingZeros(bitboard);
    }

    /**
     * Returns the number of bits set in the bitboard (population count).
     * Used for counting pieces, mobility evaluation, etc.
     *
     * @param bitboard the bitboard
     * @return the number of set bits
     */
    public static int popCount(long bitboard) {
        return Long.bitCount(bitboard);
    }

    // ========================
    // Square Coordinate Helpers
    // ========================

    /**
     * Returns the file (column) index of a square (0=a, 7=h).
     */
    public static int fileOf(int square) {
        return square & 7;
    }

    /**
     * Returns the rank (row) index of a square (0=rank 1, 7=rank 8).
     */
    public static int rankOf(int square) {
        return square >>> 3;
    }

    /**
     * Creates a square index from file and rank indices.
     *
     * @param file file index (0=a, 7=h)
     * @param rank rank index (0=rank 1, 7=rank 8)
     * @return the square index (0-63)
     */
    public static int toSquare(int file, int rank) {
        return rank * 8 + file;
    }

    /**
     * Converts a square index to algebraic notation (e.g., 0 → "a1", 63 → "h8").
     *
     * @param square the square index (0-63)
     * @return the algebraic string representation
     */
    public static String toAlgebraic(int square) {
        char file = (char) ('a' + fileOf(square));
        char rank = (char) ('1' + rankOf(square));
        return "" + file + rank;
    }

    /**
     * Parses algebraic notation to a square index (e.g., "e4" → 28).
     *
     * @param algebraic the algebraic square string (e.g., "a1", "h8")
     * @return the square index (0-63)
     * @throws IllegalArgumentException if the string is not valid algebraic notation
     */
    public static int fromAlgebraic(String algebraic) {
        if (algebraic == null || algebraic.length() != 2) {
            throw new IllegalArgumentException("Invalid algebraic notation: " + algebraic);
        }
        int file = algebraic.charAt(0) - 'a';
        int rank = algebraic.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Invalid algebraic notation: " + algebraic);
        }
        return toSquare(file, rank);
    }

    /**
     * Returns a human-readable string of the bitboard (8x8 grid).
     * Useful for debugging — prints the board from White's perspective (rank 8 at top).
     *
     * @param bitboard the bitboard to visualize
     * @return a multi-line string showing which bits are set
     */
    public static String toString(long bitboard) {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append(" | ");
            for (int file = 0; file < 8; file++) {
                int sq = toSquare(file, rank);
                sb.append(hasBit(bitboard, sq) ? "1 " : ". ");
            }
            sb.append('\n');
        }
        sb.append("    ----------------\n");
        sb.append("    a b c d e f g h\n");
        return sb.toString();
    }
}

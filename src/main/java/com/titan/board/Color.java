package com.titan.board;

/**
 * Represents the two colors in chess: White and Black.
 * <p>
 * Each color has an index (0 for White, 1 for Black) used for array lookups
 * throughout the engine — bitboard pairs, pawn attack tables, history tables, etc.
 *
 * @see <a href="https://www.chessprogramming.org/Color">Chess Programming Wiki — Color</a>
 */
public enum Color {

    WHITE(0),
    BLACK(1);

    /** Total number of colors. Used to size arrays indexed by color. */
    public static final int COUNT = 2;

    private final int index;

    Color(int index) {
        this.index = index;
    }

    /**
     * Returns the zero-based index (0=White, 1=Black).
     * Used to index into color-partitioned arrays like pawn attack tables and bitboard pairs.
     */
    public int index() {
        return index;
    }

    /**
     * Returns the opposite color.
     * Efficiently implemented using XOR on the index.
     *
     * @return BLACK if this is WHITE, WHITE if this is BLACK
     */
    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    /**
     * Returns the pawn push direction for this color.
     * White pawns advance by +8 (toward rank 8), black pawns by -8 (toward rank 1).
     *
     * @return +8 for White, -8 for Black
     */
    public int pawnDirection() {
        return this == WHITE ? 8 : -8;
    }

    /**
     * Returns true if this color is White.
     * Convenience method to avoid verbose enum comparisons in hot code paths.
     */
    public boolean isWhite() {
        return this == WHITE;
    }

    /**
     * Returns the Color for the given index (0=White, 1=Black).
     *
     * @param index the color index
     * @return the corresponding Color enum value
     */
    public static Color fromIndex(int index) {
        return VALUES[index];
    }

    /** Cached values array for O(1) index-based lookup. */
    private static final Color[] VALUES = values();
}

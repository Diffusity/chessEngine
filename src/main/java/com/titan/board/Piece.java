package com.titan.board;

/**
 * Represents the six chess piece types.
 * <p>
 * Each piece has an index (0-5) used for array lookups in bitboard and evaluation tables,
 * and a material value in centipawns for use in evaluation and move ordering (MVV-LVA).
 * <p>
 * The ordering (PAWN first, KING last) is a deliberate convention used across the engine:
 * bitboard arrays, piece-square tables, and Zobrist key arrays all use this indexing.
 *
 * @see <a href="https://www.chessprogramming.org/Pieces">Chess Programming Wiki — Pieces</a>
 */
public enum Piece {

    PAWN(0, 100, 'P'),
    KNIGHT(1, 320, 'N'),
    BISHOP(2, 330, 'B'),
    ROOK(3, 500, 'R'),
    QUEEN(4, 900, 'Q'),
    KING(5, 20_000, 'K');

    /** Total number of distinct piece types. Used to size arrays indexed by piece. */
    public static final int COUNT = 6;

    private final int index;
    private final int value;
    private final char symbol;

    Piece(int index, int value, char symbol) {
        this.index = index;
        this.value = value;
        this.symbol = symbol;
    }

    /**
     * Returns the zero-based index of this piece type (0=Pawn, 5=King).
     * Used to index into bitboard arrays, PST tables, and Zobrist keys.
     */
    public int index() {
        return index;
    }

    /**
     * Returns the material value in centipawns.
     * Used in evaluation and MVV-LVA move ordering.
     */
    public int value() {
        return value;
    }

    /**
     * Returns the uppercase character symbol for this piece (e.g., 'P', 'N', 'B').
     * Used in FEN parsing and display.
     */
    public char symbol() {
        return symbol;
    }

    /**
     * Returns the character for this piece with the correct case for the given color.
     * White pieces are uppercase, black pieces are lowercase (FEN convention).
     *
     * @param white true for white pieces (uppercase), false for black (lowercase)
     * @return the piece character in the appropriate case
     */
    public char toChar(boolean white) {
        return white ? symbol : Character.toLowerCase(symbol);
    }

    /**
     * Parses a piece from its FEN character representation (case-insensitive).
     *
     * @param c the piece character ('P','N','B','R','Q','K' — upper or lowercase)
     * @return the corresponding Piece enum value
     * @throws IllegalArgumentException if the character is not a valid piece
     */
    public static Piece fromChar(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'P' -> PAWN;
            case 'N' -> KNIGHT;
            case 'B' -> BISHOP;
            case 'R' -> ROOK;
            case 'Q' -> QUEEN;
            case 'K' -> KING;
            default -> throw new IllegalArgumentException("Invalid piece character: " + c);
        };
    }

    /**
     * Returns the Piece for the given index (0-5).
     * This is the inverse of {@link #index()}.
     *
     * @param index the piece index (0=Pawn, 5=King)
     * @return the corresponding Piece enum value
     * @throws ArrayIndexOutOfBoundsException if index is out of range
     */
    public static Piece fromIndex(int index) {
        return VALUES[index];
    }

    /** Cached values array for O(1) index-based lookup, avoiding repeated calls to values(). */
    private static final Piece[] VALUES = values();
}

package com.titan.board;

/**
 * Utility class for castling-related constants and logic.
 * <p>
 * Handles standard chess castling (not Chess960).
 * Provides the rook's from/to squares for each castling type,
 * and a table for efficiently updating castling rights when pieces move.
 *
 * @see <a href="https://www.chessprogramming.org/Castling">Chess Programming Wiki — Castling</a>
 */
public final class Castling {

    private Castling() {
        // Utility class
    }

    // ========================
    // King and Rook squares for standard castling
    // ========================

    // White castling
    /** White king's starting square. */
    public static final int WHITE_KING_FROM = Bits.Square.E1;
    /** White king's destination for kingside castling. */
    public static final int WHITE_KING_TO_KINGSIDE = Bits.Square.G1;
    /** White king's destination for queenside castling. */
    public static final int WHITE_KING_TO_QUEENSIDE = Bits.Square.C1;
    /** White rook's starting square for kingside castling. */
    public static final int WHITE_ROOK_FROM_KINGSIDE = Bits.Square.H1;
    /** White rook's destination for kingside castling. */
    public static final int WHITE_ROOK_TO_KINGSIDE = Bits.Square.F1;
    /** White rook's starting square for queenside castling. */
    public static final int WHITE_ROOK_FROM_QUEENSIDE = Bits.Square.A1;
    /** White rook's destination for queenside castling. */
    public static final int WHITE_ROOK_TO_QUEENSIDE = Bits.Square.D1;

    // Black castling
    /** Black king's starting square. */
    public static final int BLACK_KING_FROM = Bits.Square.E8;
    /** Black king's destination for kingside castling. */
    public static final int BLACK_KING_TO_KINGSIDE = Bits.Square.G8;
    /** Black king's destination for queenside castling. */
    public static final int BLACK_KING_TO_QUEENSIDE = Bits.Square.C8;
    /** Black rook's starting square for kingside castling. */
    public static final int BLACK_ROOK_FROM_KINGSIDE = Bits.Square.H8;
    /** Black rook's destination for kingside castling. */
    public static final int BLACK_ROOK_TO_KINGSIDE = Bits.Square.F8;
    /** Black rook's starting square for queenside castling. */
    public static final int BLACK_ROOK_FROM_QUEENSIDE = Bits.Square.A8;
    /** Black rook's destination for queenside castling. */
    public static final int BLACK_ROOK_TO_QUEENSIDE = Bits.Square.D8;

    // ========================
    // Path masks — squares that must be empty for castling
    // ========================

    /** Squares between king and rook for white kingside castling (f1, g1). */
    public static final long WHITE_KINGSIDE_PATH = Bits.toBitboard(Bits.Square.F1) | Bits.toBitboard(Bits.Square.G1);
    /** Squares between king and rook for white queenside castling (b1, c1, d1). */
    public static final long WHITE_QUEENSIDE_PATH = Bits.toBitboard(Bits.Square.B1) | Bits.toBitboard(Bits.Square.C1) | Bits.toBitboard(Bits.Square.D1);
    /** Squares between king and rook for black kingside castling (f8, g8). */
    public static final long BLACK_KINGSIDE_PATH = Bits.toBitboard(Bits.Square.F8) | Bits.toBitboard(Bits.Square.G8);
    /** Squares between king and rook for black queenside castling (b8, c8, d8). */
    public static final long BLACK_QUEENSIDE_PATH = Bits.toBitboard(Bits.Square.B8) | Bits.toBitboard(Bits.Square.C8) | Bits.toBitboard(Bits.Square.D8);

    // ========================
    // Castling rights update table
    // ========================

    /**
     * Lookup table indexed by square (0-63) that returns a mask to AND with castling rights.
     * When a piece moves from or to a given square, AND the castling rights with this mask
     * to revoke any rights that are affected.
     * <p>
     * For example, if a rook on h1 moves, we AND with ~WHITE_KING_SIDE to remove that right.
     * Most squares return 0xF (all rights preserved).
     */
    public static final int[] CASTLING_RIGHTS_UPDATE = new int[Bits.Square.COUNT];

    static {
        // Initialize all squares to preserve all rights
        for (int sq = 0; sq < Bits.Square.COUNT; sq++) {
            CASTLING_RIGHTS_UPDATE[sq] = BoardState.ALL_CASTLING;
        }

        // When king or rook moves from its starting square, revoke the corresponding right
        CASTLING_RIGHTS_UPDATE[WHITE_KING_FROM]           &= ~(BoardState.WHITE_KING_SIDE | BoardState.WHITE_QUEEN_SIDE);
        CASTLING_RIGHTS_UPDATE[WHITE_ROOK_FROM_KINGSIDE]  &= ~BoardState.WHITE_KING_SIDE;
        CASTLING_RIGHTS_UPDATE[WHITE_ROOK_FROM_QUEENSIDE] &= ~BoardState.WHITE_QUEEN_SIDE;
        CASTLING_RIGHTS_UPDATE[BLACK_KING_FROM]           &= ~(BoardState.BLACK_KING_SIDE | BoardState.BLACK_QUEEN_SIDE);
        CASTLING_RIGHTS_UPDATE[BLACK_ROOK_FROM_KINGSIDE]  &= ~BoardState.BLACK_KING_SIDE;
        CASTLING_RIGHTS_UPDATE[BLACK_ROOK_FROM_QUEENSIDE] &= ~BoardState.BLACK_QUEEN_SIDE;
    }

    // ========================
    // Helper Methods
    // ========================

    /**
     * Determines if a castling move is kingside based on king's destination.
     *
     * @param from the king's origin square
     * @param to   the king's destination square
     * @return true if this is kingside castling
     */
    public static boolean isKingside(int from, int to) {
        return to > from;
    }

    /**
     * Returns the rook's starting square for the given castling type.
     *
     * @param kingside true for kingside, false for queenside
     * @param white    true for White, false for Black
     * @return the rook's origin square index
     */
    public static int rookFrom(boolean kingside, boolean white) {
        if (white) {
            return kingside ? WHITE_ROOK_FROM_KINGSIDE : WHITE_ROOK_FROM_QUEENSIDE;
        } else {
            return kingside ? BLACK_ROOK_FROM_KINGSIDE : BLACK_ROOK_FROM_QUEENSIDE;
        }
    }

    /**
     * Returns the rook's destination square for the given castling type.
     *
     * @param kingside true for kingside, false for queenside
     * @param white    true for White, false for Black
     * @return the rook's destination square index
     */
    public static int rookTo(boolean kingside, boolean white) {
        if (white) {
            return kingside ? WHITE_ROOK_TO_KINGSIDE : WHITE_ROOK_TO_QUEENSIDE;
        } else {
            return kingside ? BLACK_ROOK_TO_KINGSIDE : BLACK_ROOK_TO_QUEENSIDE;
        }
    }

    /**
     * Returns the path mask (squares that must be empty) for the given castling type.
     *
     * @param kingside true for kingside, false for queenside
     * @param white    true for White, false for Black
     * @return bitboard of squares that must be unoccupied
     */
    public static long path(boolean kingside, boolean white) {
        if (white) {
            return kingside ? WHITE_KINGSIDE_PATH : WHITE_QUEENSIDE_PATH;
        } else {
            return kingside ? BLACK_KINGSIDE_PATH : BLACK_QUEENSIDE_PATH;
        }
    }
}

package com.titan.board;

/**
 * Captures the mutable, incremental state of a chess position that changes with each move.
 * <p>
 * This class holds the information that must be saved before a move is made and restored
 * when the move is unmade. By storing this in a stack of states, the engine can efficiently
 * undo moves without recomputing the entire position.
 * <p>
 * Fields tracked:
 * <ul>
 *   <li><b>Castling rights</b> — 4-bit mask (KQkq)</li>
 *   <li><b>En passant square</b> — the target square, or NONE if unavailable</li>
 *   <li><b>Half-move clock</b> — for the 50-move rule</li>
 *   <li><b>Captured piece</b> — the piece removed by the last move (for unmake)</li>
 *   <li><b>Zobrist key</b> — incrementally updated position hash</li>
 *   <li><b>Pawn key</b> — hash of pawn structure only (for pawn eval caching)</li>
 *   <li><b>Checkers</b> — bitboard of pieces giving check</li>
 * </ul>
 *
 * @see Board#makeMove(Move)
 * @see Board#unmakeMove()
 */
public class BoardState {

    // ========================
    // Castling Rights (4-bit mask)
    // ========================

    /** White can castle kingside (O-O). */
    public static final int WHITE_KING_SIDE  = 0b0001;
    /** White can castle queenside (O-O-O). */
    public static final int WHITE_QUEEN_SIDE = 0b0010;
    /** Black can castle kingside (O-O). */
    public static final int BLACK_KING_SIDE  = 0b0100;
    /** Black can castle queenside (O-O-O). */
    public static final int BLACK_QUEEN_SIDE = 0b1000;
    /** All castling rights set. */
    public static final int ALL_CASTLING     = 0b1111;
    /** No castling rights. */
    public static final int NO_CASTLING      = 0b0000;

    // ========================
    // State Fields
    // ========================

    /** 4-bit castling rights mask. */
    int castlingRights;

    /** En passant target square index (0-63), or {@link Bits.Square#NONE} if not available. */
    int enPassantSquare;

    /** Half-move clock for the 50-move draw rule. Resets on pawn move or capture. */
    int halfMoveClock;

    /** The piece captured by the last move, or null if no capture. Used for unmakeMove(). */
    Piece capturedPiece;

    /** Zobrist hash key for the full position. Incrementally updated on each move. */
    long key;

    /** Zobrist hash key for the pawn structure only. Used for pawn evaluation caching. */
    long pawnKey;

    /** Bitboard of enemy pieces giving check to the side to move. Zero if not in check. */
    long checkers;

    // ========================
    // Construction
    // ========================

    /**
     * Creates a default BoardState (all castling rights, no en passant, clock at 0).
     */
    public BoardState() {
        this.castlingRights = ALL_CASTLING;
        this.enPassantSquare = Bits.Square.NONE;
        this.halfMoveClock = 0;
        this.capturedPiece = null;
        this.key = 0L;
        this.pawnKey = 0L;
        this.checkers = 0L;
    }

    /**
     * Creates a BoardState with specific initial values.
     */
    public BoardState(int castlingRights, int enPassantSquare, int halfMoveClock) {
        this.castlingRights = castlingRights;
        this.enPassantSquare = enPassantSquare;
        this.halfMoveClock = halfMoveClock;
        this.capturedPiece = null;
        this.key = 0L;
        this.pawnKey = 0L;
        this.checkers = 0L;
    }

    /**
     * Creates a deep copy of this state. Used to save state before making a move.
     *
     * @return a new BoardState with identical field values
     */
    public BoardState copy() {
        BoardState copy = new BoardState();
        copyInto(copy);
        return copy;
    }

    /**
     * Copies this state's fields into the given target object without allocating.
     * Used by the pre-allocated undo stack in {@link Board}.
     *
     * @param target the state object to overwrite with this state's values
     */
    public void copyInto(BoardState target) {
        target.castlingRights = this.castlingRights;
        target.enPassantSquare = this.enPassantSquare;
        target.halfMoveClock = this.halfMoveClock;
        target.capturedPiece = this.capturedPiece;
        target.key = this.key;
        target.pawnKey = this.pawnKey;
        target.checkers = this.checkers;
    }

    // ========================
    // Castling Rights Helpers
    // ========================

    /**
     * Checks if a specific castling right is still available.
     *
     * @param right one of the castling right constants (e.g., WHITE_KING_SIDE)
     * @return true if the right is still available
     */
    public boolean hasCastlingRight(int right) {
        return (castlingRights & right) != 0;
    }

    /**
     * Removes a castling right.
     *
     * @param right the right to remove
     */
    public void removeCastlingRight(int right) {
        castlingRights &= ~right;
    }

    /**
     * Returns true if the side to move has any castling rights.
     *
     * @param white true to check White's rights, false for Black's
     * @return true if at least one castling right exists
     */
    public boolean hasAnyCastlingRight(boolean white) {
        int mask = white ? (WHITE_KING_SIDE | WHITE_QUEEN_SIDE) : (BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
        return (castlingRights & mask) != 0;
    }

    // ========================
    // Accessors
    // ========================

    public int getCastlingRights() { return castlingRights; }
    public int getEnPassantSquare() { return enPassantSquare; }
    public int getHalfMoveClock() { return halfMoveClock; }
    public Piece getCapturedPiece() { return capturedPiece; }
    public long getKey() { return key; }
    public long getPawnKey() { return pawnKey; }
    public long getCheckers() { return checkers; }

    @Override
    public String toString() {
        return String.format("BoardState[castling=%s, ep=%s, halfmove=%d, key=%016x]",
                Integer.toBinaryString(castlingRights),
                enPassantSquare == Bits.Square.NONE ? "-" : Bits.toAlgebraic(enPassantSquare),
                halfMoveClock, key);
    }
}

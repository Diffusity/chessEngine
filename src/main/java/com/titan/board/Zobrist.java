package com.titan.board;

import java.util.Random;

/**
 * Zobrist hashing for chess positions.
 * <p>
 * Zobrist hashing assigns a random 64-bit key to every possible (piece, color, square)
 * combination, plus keys for castling rights, en passant files, and side to move.
 * A position's hash is computed by XOR-ing together all applicable keys.
 * <p>
 * The critical property is that the hash can be <b>incrementally updated</b>:
 * when a piece moves from square A to square B, we simply XOR out the key for A
 * and XOR in the key for B. This makes make/unmake O(1) for hash updates.
 * <p>
 * Zobrist hashing is used for:
 * <ul>
 *   <li>Transposition table indexing and verification</li>
 *   <li>Repetition detection (comparing position keys)</li>
 *   <li>Pawn structure caching (pawn-only key)</li>
 * </ul>
 *
 * @see <a href="https://www.chessprogramming.org/Zobrist_Hashing">Chess Programming Wiki — Zobrist Hashing</a>
 */
public final class Zobrist {

    private Zobrist() {
        // Utility class
    }

    /**
     * Random keys for (color, piece, square) combinations.
     * Indexed as: PIECE_KEYS[color][piece][square]
     * <p>
     * Dimensions: [2 colors][6 pieces][64 squares]
     */
    private static final long[][][] PIECE_KEYS = new long[Color.COUNT][Piece.COUNT][Bits.Square.COUNT];

    /**
     * Random keys for castling rights.
     * There are 16 possible castling states (4-bit mask), each with its own key.
     */
    private static final long[] CASTLING_KEYS = new long[16];

    /**
     * Random keys for en passant files (0-7, one per file).
     * We only need to hash the file, not the square, because the rank is implied
     * by the side to move.
     */
    private static final long[] EN_PASSANT_KEYS = new long[8];

    /**
     * Key XOR'd in when it is Black's turn to move.
     * When it's White's turn, this key is not included in the hash.
     */
    private static final long SIDE_KEY;

    // ========================
    // Static Initialization
    // ========================

    static {
        // Use a fixed seed for reproducible hashing across runs.
        // The specific seed value doesn't matter — what matters is consistency.
        Random rng = new Random(0x5D794E3B1A6F2C08L);

        for (int color = 0; color < Color.COUNT; color++) {
            for (int piece = 0; piece < Piece.COUNT; piece++) {
                for (int sq = 0; sq < Bits.Square.COUNT; sq++) {
                    PIECE_KEYS[color][piece][sq] = rng.nextLong();
                }
            }
        }

        for (int i = 0; i < CASTLING_KEYS.length; i++) {
            CASTLING_KEYS[i] = rng.nextLong();
        }

        for (int i = 0; i < EN_PASSANT_KEYS.length; i++) {
            EN_PASSANT_KEYS[i] = rng.nextLong();
        }

        SIDE_KEY = rng.nextLong();
    }

    // ========================
    // Key Access Methods
    // ========================

    /**
     * Returns the Zobrist key for a piece of a given color on a given square.
     *
     * @param white  true for White, false for Black
     * @param piece  the piece type
     * @param square the square index (0-63)
     * @return the random key for this (color, piece, square) combination
     */
    public static long pieceKey(boolean white, Piece piece, int square) {
        return PIECE_KEYS[white ? 0 : 1][piece.index()][square];
    }

    /**
     * Returns the Zobrist key for the given castling rights.
     *
     * @param castlingRights the 4-bit castling rights mask
     * @return the random key for this castling state
     */
    public static long castlingKey(int castlingRights) {
        return CASTLING_KEYS[castlingRights];
    }

    /**
     * Returns the Zobrist key for an en passant file.
     *
     * @param square the en passant target square (0-63)
     * @return the random key for the file of this square
     */
    public static long enPassantKey(int square) {
        if (square == Bits.Square.NONE) return 0L;
        return EN_PASSANT_KEYS[Bits.fileOf(square)];
    }

    /**
     * Returns the Zobrist key for the side to move (XOR'd when Black is to move).
     */
    public static long sideKey() {
        return SIDE_KEY;
    }

    // ========================
    // Full Key Computation
    // ========================

    /**
     * Computes the full Zobrist key for a position from scratch.
     * This is used during initialization (e.g., when loading a FEN string).
     * During gameplay, the key is updated incrementally in makeMove/unmakeMove.
     *
     * @param board the board to compute the key for
     * @return the full Zobrist hash key
     */
    public static long computeKey(Board board) {
        long key = 0L;

        // Hash all pieces on the board
        for (int sq = 0; sq < Bits.Square.COUNT; sq++) {
            Piece piece = board.pieceAt(sq);
            if (piece != null) {
                boolean white = Bits.hasBit(board.getWhitePieces(), sq);
                key ^= pieceKey(white, piece, sq);
            }
        }

        // Hash castling rights
        key ^= castlingKey(board.getState().getCastlingRights());

        // Hash en passant
        key ^= enPassantKey(board.getState().getEnPassantSquare());

        // Hash side to move
        if (!board.isWhiteToMove()) {
            key ^= sideKey();
        }

        return key;
    }

    /**
     * Computes the pawn-only Zobrist key from scratch.
     * Used for pawn structure evaluation caching.
     *
     * @param board the board
     * @return the pawn structure hash key
     */
    public static long computePawnKey(Board board) {
        long key = 0L;
        for (int color = 0; color < Color.COUNT; color++) {
            boolean white = (color == 0);
            long pawns = board.getBitboard(Piece.PAWN, white);
            while (pawns != 0) {
                int sq = Bits.lsb(pawns);
                key ^= pieceKey(white, Piece.PAWN, sq);
                pawns &= pawns - 1; // clear LSB
            }
        }
        return key;
    }
}

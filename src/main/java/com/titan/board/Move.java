package com.titan.board;

/**
 * Represents a chess move using a compact 16-bit integer encoding.
 * <p>
 * The encoding packs the from-square, to-square, and move flags into a single {@code int}:
 * <pre>
 *   Bits  0-5:  From square (0-63)
 *   Bits  6-11: To square (0-63)
 *   Bits 12-15: Flags (move type)
 * </pre>
 * <p>
 * This compact encoding minimizes memory usage in move lists and transposition table entries,
 * and allows moves to be compared with simple integer equality.
 *
 * @see <a href="https://www.chessprogramming.org/Encoding_Moves">Chess Programming Wiki — Encoding Moves</a>
 */
public class Move {

    // ========================
    // Flag Constants (bits 12-15)
    // ========================

    /** Quiet move — no capture, no special action. */
    public static final int FLAG_QUIET            = 0b0000;
    /** Pawn double push (e.g., e2→e4). Sets en passant square. */
    public static final int FLAG_PAWN_DOUBLE       = 0b0001;
    /** Kingside castling (O-O). */
    public static final int FLAG_CASTLE_KING       = 0b0010;
    /** Queenside castling (O-O-O). */
    public static final int FLAG_CASTLE_QUEEN      = 0b0011;
    /** Standard capture. */
    public static final int FLAG_CAPTURE           = 0b0100;
    /** En passant capture. */
    public static final int FLAG_EP_CAPTURE        = 0b0101;
    /** Promotion to knight (quiet). */
    public static final int FLAG_PROMO_KNIGHT      = 0b1000;
    /** Promotion to bishop (quiet). */
    public static final int FLAG_PROMO_BISHOP      = 0b1001;
    /** Promotion to rook (quiet). */
    public static final int FLAG_PROMO_ROOK        = 0b1010;
    /** Promotion to queen (quiet). */
    public static final int FLAG_PROMO_QUEEN       = 0b1011;
    /** Promotion to knight with capture. */
    public static final int FLAG_PROMO_KNIGHT_CAP  = 0b1100;
    /** Promotion to bishop with capture. */
    public static final int FLAG_PROMO_BISHOP_CAP  = 0b1101;
    /** Promotion to rook with capture. */
    public static final int FLAG_PROMO_ROOK_CAP    = 0b1110;
    /** Promotion to queen with capture. */
    public static final int FLAG_PROMO_QUEEN_CAP   = 0b1111;

    /** Sentinel value representing "no move" (e.g., empty TT entry). */
    public static final int NONE = 0;

    // ========================
    // Encoding Masks
    // ========================

    private static final int FROM_MASK  = 0x3F;        // bits 0-5
    private static final int TO_MASK    = 0x3F;        // bits 6-11 (after shift)
    private static final int FLAG_MASK  = 0x0F;        // bits 12-15 (after shift)
    private static final int TO_SHIFT   = 6;
    private static final int FLAG_SHIFT = 12;

    // The encoded move value
    private final int value;

    // ========================
    // Construction
    // ========================

    private Move(int value) {
        this.value = value;
    }

    /**
     * Creates a move from the given from-square, to-square, and flags.
     *
     * @param from  the origin square (0-63)
     * @param to    the destination square (0-63)
     * @param flags the move flag (see FLAG_* constants)
     * @return the encoded move
     */
    public static Move of(int from, int to, int flags) {
        return new Move((from & FROM_MASK) | ((to & TO_MASK) << TO_SHIFT) | ((flags & FLAG_MASK) << FLAG_SHIFT));
    }

    /**
     * Creates a quiet (non-capture, non-special) move.
     */
    public static Move ofQuiet(int from, int to) {
        return of(from, to, FLAG_QUIET);
    }

    /**
     * Creates a capture move.
     */
    public static Move ofCapture(int from, int to) {
        return of(from, to, FLAG_CAPTURE);
    }

    /**
     * Reconstructs a Move from its raw encoded int value (e.g., from a TT entry).
     *
     * @param encoded the raw int encoding
     * @return the Move object, or null if the value is NONE
     */
    public static Move fromEncoded(int encoded) {
        if (encoded == NONE) return null;
        return new Move(encoded);
    }

    // ========================
    // Decoding
    // ========================

    /** Returns the origin square index (0-63). */
    public int from() {
        return value & FROM_MASK;
    }

    /** Returns the destination square index (0-63). */
    public int to() {
        return (value >>> TO_SHIFT) & TO_MASK;
    }

    /** Returns the move flags (bits 12-15). */
    public int flags() {
        return (value >>> FLAG_SHIFT) & FLAG_MASK;
    }

    /** Returns the raw encoded integer value. */
    public int encoded() {
        return value;
    }

    // ========================
    // Flag Query Methods
    // ========================

    /** True if this move captures a piece (standard capture, EP, or promotion-capture). */
    public boolean isCapture() {
        int f = flags();
        return f == FLAG_CAPTURE || f == FLAG_EP_CAPTURE || (f & 0b1100) == 0b1100;
    }

    /** True if this is an en passant capture. */
    public boolean isEnPassant() {
        return flags() == FLAG_EP_CAPTURE;
    }

    /** True if this is a pawn double push (creates en passant opportunity). */
    public boolean isPawnDoubleMove() {
        return flags() == FLAG_PAWN_DOUBLE;
    }

    /** True if this is a castling move (kingside or queenside). */
    public boolean isCastling() {
        int f = flags();
        return f == FLAG_CASTLE_KING || f == FLAG_CASTLE_QUEEN;
    }

    /** True if this is a promotion move (with or without capture). */
    public boolean isPromotion() {
        return (flags() & 0b1000) != 0;
    }

    /**
     * Returns the piece type being promoted to, or null if not a promotion.
     * Promotion flags encode the piece type in the lower 2 bits (after the promo bit):
     * 00=Knight, 01=Bishop, 10=Rook, 11=Queen.
     */
    public Piece promoPiece() {
        if (!isPromotion()) return null;
        return switch (flags() & 0b0011) {
            case 0b00 -> Piece.KNIGHT;
            case 0b01 -> Piece.BISHOP;
            case 0b10 -> Piece.ROOK;
            case 0b11 -> Piece.QUEEN;
            default -> throw new IllegalStateException("Invalid promotion flag");
        };
    }

    /** True if this is a quiet move (no capture, no promotion, no castling, no special). */
    public boolean isQuiet() {
        return !isCapture() && !isPromotion();
    }

    // ========================
    // UCI Notation
    // ========================

    /**
     * Converts this move to UCI long algebraic notation (e.g., "e2e4", "e7e8q").
     * This is the format used to communicate moves via the UCI protocol.
     *
     * @return the move in UCI notation
     */
    public String toUCI() {
        String base = Bits.toAlgebraic(from()) + Bits.toAlgebraic(to());
        if (isPromotion()) {
            base += Character.toLowerCase(promoPiece().symbol());
        }
        return base;
    }

    /**
     * Parses a UCI-notation move string (e.g., "e2e4", "e7e8q").
     * <p>
     * Note: This creates the move without full context (the flag is inferred from the string alone).
     * For fully-correct flag assignment, use a board-aware parser that can detect captures,
     * en passant, castling, and pawn double pushes.
     *
     * @param uci the UCI move string
     * @return the parsed Move
     */
    public static Move fromUCI(String uci) {
        int from = Bits.fromAlgebraic(uci.substring(0, 2));
        int to = Bits.fromAlgebraic(uci.substring(2, 4));

        if (uci.length() == 5) {
            char promo = uci.charAt(4);
            int promoFlag = switch (Character.toLowerCase(promo)) {
                case 'n' -> FLAG_PROMO_KNIGHT;
                case 'b' -> FLAG_PROMO_BISHOP;
                case 'r' -> FLAG_PROMO_ROOK;
                case 'q' -> FLAG_PROMO_QUEEN;
                default -> throw new IllegalArgumentException("Invalid promotion piece: " + promo);
            };
            return of(from, to, promoFlag);
        }
        return of(from, to, FLAG_QUIET);
    }

    // ========================
    // Object Methods
    // ========================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Move other)) return false;
        return this.value == other.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return toUCI();
    }
}

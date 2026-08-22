package com.titan.board;

import java.util.Arrays;

/**
 * Represents the current state of the chess board using a bitboard-based representation.
 * <p>
 * The board maintains two parallel data structures:
 * <ol>
 *   <li><b>Bitboards</b> — 12 longs (6 pieces × 2 colors) for efficient bulk operations
 *       (attack generation, piece counting, pattern detection)</li>
 *   <li><b>Mailbox</b> — 64-element Piece array for O(1) "what piece is on this square?" queries</li>
 * </ol>
 * <p>
 * The board supports make/unmake move operations that incrementally update all internal state
 * including bitboards, mailbox, Zobrist hash, castling rights, en passant, and half-move clock.
 * State is saved on a stack before each move so that unmakeMove() can restore it in O(1).
 *
 * @see <a href="https://www.chessprogramming.org/Board_Representation">Chess Programming Wiki — Board Representation</a>
 */
public class Board {

    /** Maximum number of plies (half-moves) in a game. Used to size state/move history arrays. */
    public static final int MAX_PLY = 1024;

    // ========================
    // Board Data
    // ========================

    /**
     * Bitboards for each piece type and color.
     * Index: [color_index * 6 + piece_index]
     * White pieces: indices 0-5, Black pieces: indices 6-11
     * Index 12: all White pieces (occupancy), Index 13: all Black pieces (occupancy)
     */
    private long[] bitboards;

    /** Mailbox array — maps each square (0-63) to the Piece on that square, or null if empty. */
    private Piece[] mailbox;

    /** Which color occupies each square: true = White, false = Black. Only valid when mailbox[sq] != null. */
    private boolean[] pieceColors;

    /** The current board state (castling, EP, halfmove clock, Zobrist key, etc.). */
    private BoardState state;

    /** Stack of saved board states for unmakeMove(). Slots are recycled from {@link #stateFreeList}. */
    private BoardState[] stateHistory;

    /** Pool of unused BoardState objects handed out on make and returned on unmake. */
    private BoardState[] stateFreeList;
    private int stateFreeCount;

    /** Stack of moves made so far. */
    private Move[] moveHistory;

    /** True if it's White's turn to move. */
    private boolean white;

    /** Current ply (half-move count from root of the game). */
    private int ply;

    /** Full move number (starts at 1, increments after Black moves). */
    private int fullMoveNumber;

    /** Array of Zobrist keys for all positions encountered, for repetition detection. */
    private long[] keyHistory;

    // ========================
    // Construction
    // ========================

    /**
     * Creates an empty board. Use {@link #startingPosition()} or a FEN parser to set up pieces.
     */
    public Board() {
        this.bitboards    = new long[14]; // 6 pieces × 2 colors + 2 occupancy
        this.mailbox      = new Piece[Bits.Square.COUNT];
        this.pieceColors  = new boolean[Bits.Square.COUNT];
        this.stateHistory = new BoardState[MAX_PLY];
        this.stateFreeList = new BoardState[MAX_PLY + 1];
        for (int i = 0; i < MAX_PLY + 1; i++) {
            stateFreeList[i] = new BoardState();
        }
        this.stateFreeCount = MAX_PLY + 1;
        // Take the live state out of the pool
        this.state = stateFreeList[--stateFreeCount];
        this.moveHistory  = new Move[MAX_PLY];
        this.keyHistory   = new long[MAX_PLY];
        this.state        = new BoardState();
        this.white        = true;
        this.ply          = 0;
        this.fullMoveNumber = 1;
    }

    /**
     * Creates a board set to the standard starting position.
     *
     * @return a new Board with all pieces in their initial positions
     */
    public static Board startingPosition() {
        Board board = new Board();
        board.setupStartingPosition();
        return board;
    }

    // ========================
    // Piece Placement
    // ========================

    /**
     * Places a piece on the board. Updates bitboards, mailbox, and Zobrist key.
     *
     * @param piece  the piece type to place
     * @param square the square to place it on (0-63)
     * @param isWhite true for White, false for Black
     */
    public void addPiece(Piece piece, int square, boolean isWhite) {
        int bbIndex = bbIndex(piece, isWhite);
        int occIndex = isWhite ? 12 : 13;

        bitboards[bbIndex] = Bits.setBit(bitboards[bbIndex], square);
        bitboards[occIndex] = Bits.setBit(bitboards[occIndex], square);
        mailbox[square] = piece;
        pieceColors[square] = isWhite;

        // Update Zobrist key
        state.key ^= Zobrist.pieceKey(isWhite, piece, square);
        if (piece == Piece.PAWN) {
            state.pawnKey ^= Zobrist.pieceKey(isWhite, piece, square);
        }
    }

    /**
     * Removes a piece from the board. Updates bitboards, mailbox, and Zobrist key.
     *
     * @param piece  the piece type to remove
     * @param square the square to remove from (0-63)
     * @param isWhite true for White, false for Black
     */
    public void removePiece(Piece piece, int square, boolean isWhite) {
        int bbIndex = bbIndex(piece, isWhite);
        int occIndex = isWhite ? 12 : 13;

        bitboards[bbIndex] = Bits.clearBit(bitboards[bbIndex], square);
        bitboards[occIndex] = Bits.clearBit(bitboards[occIndex], square);
        mailbox[square] = null;

        // Update Zobrist key
        state.key ^= Zobrist.pieceKey(isWhite, piece, square);
        if (piece == Piece.PAWN) {
            state.pawnKey ^= Zobrist.pieceKey(isWhite, piece, square);
        }
    }

    /**
     * Moves a piece from one square to another. Does NOT handle captures.
     *
     * @param piece  the piece type
     * @param from   the origin square
     * @param to     the destination square
     * @param isWhite true for White, false for Black
     */
    private void movePiece(Piece piece, int from, int to, boolean isWhite) {
        int bbIndex = bbIndex(piece, isWhite);
        int occIndex = isWhite ? 12 : 13;
        long fromTo = Bits.toBitboard(from) | Bits.toBitboard(to);

        bitboards[bbIndex] ^= fromTo;
        bitboards[occIndex] ^= fromTo;
        mailbox[from] = null;
        mailbox[to] = piece;
        pieceColors[to] = isWhite;

        // Update Zobrist key (XOR out old, XOR in new)
        state.key ^= Zobrist.pieceKey(isWhite, piece, from);
        state.key ^= Zobrist.pieceKey(isWhite, piece, to);
        if (piece == Piece.PAWN) {
            state.pawnKey ^= Zobrist.pieceKey(isWhite, piece, from);
            state.pawnKey ^= Zobrist.pieceKey(isWhite, piece, to);
        }
    }

    // ========================
    // Make Move
    // ========================

    /**
     * Applies a move to the board, updating all internal state.
     * <p>
     * Saves the current state to the history stack so it can be restored by {@link #unmakeMove()}.
     * Handles all special moves: pawn double push, castling, en passant, promotion.
     *
     * @param move the move to apply
     * @return true if the move was successfully applied (piece existed on from-square)
     */
    public boolean makeMove(Move move) {
        int from = move.from();
        int to = move.to();
        Piece piece = mailbox[from];

        if (piece == null) return false;

        // Save state for undo: snapshot into a recycled object guaranteed distinct
        // from the live `state` (free-list invariant), so no aliasing occurs.
        BoardState snap = stateFreeList[--stateFreeCount];
        state.copyInto(snap);
        stateHistory[ply] = snap;
        keyHistory[ply] = state.key;

        // Remove old en passant key from hash
        state.key ^= Zobrist.enPassantKey(state.enPassantSquare);
        // Remove old castling key from hash
        state.key ^= Zobrist.castlingKey(state.castlingRights);

        // Determine captured piece
        Piece captured = move.isEnPassant() ? Piece.PAWN : mailbox[to];
        state.capturedPiece = captured;

        // Update half-move clock
        if (piece == Piece.PAWN || captured != null) {
            state.halfMoveClock = 0;
        } else {
            state.halfMoveClock++;
        }

        // Reset en passant
        state.enPassantSquare = Bits.Square.NONE;

        // Execute the move based on type
        if (move.isPawnDoubleMove()) {
            makePawnDoubleMove(from, to);
        } else if (move.isCastling()) {
            makeCastlingMove(from, to);
        } else if (move.isEnPassant()) {
            makeEnPassantMove(from, to);
        } else if (move.isPromotion()) {
            makePromotionMove(from, to, move.promoPiece(), captured);
        } else {
            makeStandardMove(from, to, piece, captured);
        }

        // Update castling rights
        state.castlingRights &= Castling.CASTLING_RIGHTS_UPDATE[from];
        state.castlingRights &= Castling.CASTLING_RIGHTS_UPDATE[to];

        // Add new castling and en passant keys to hash
        state.key ^= Zobrist.castlingKey(state.castlingRights);
        state.key ^= Zobrist.enPassantKey(state.enPassantSquare);

        // Flip side to move
        state.key ^= Zobrist.sideKey();

        // Record move
        moveHistory[ply] = move;
        ply++;

        // Update full move number
        if (!white) {
            fullMoveNumber++;
        }

        white = !white;
        return true;
    }

    private void makePawnDoubleMove(int from, int to) {
        movePiece(Piece.PAWN, from, to, white);
        // Set en passant square only if an enemy pawn could actually capture there.
        // Otherwise leave NONE so functionally identical positions share one Zobrist
        // key (better TT hits + correct repetition detection).
        int epSq = from + (white ? 8 : -8);
        long enemyPawns = getBitboard(Piece.PAWN, !white);
        if ((com.titan.movegen.Attacks.PAWN_ATTACKS[white ? 0 : 1][epSq] & enemyPawns) != 0) {
            state.enPassantSquare = epSq;
        }
    }

    private void makeCastlingMove(int from, int to) {
        boolean kingside = Castling.isKingside(from, to);

        // Move king
        movePiece(Piece.KING, from, to, white);

        // Move rook
        int rookFrom = Castling.rookFrom(kingside, white);
        int rookTo = Castling.rookTo(kingside, white);
        movePiece(Piece.ROOK, rookFrom, rookTo, white);
    }

    private void makeEnPassantMove(int from, int to) {
        // Move the capturing pawn
        movePiece(Piece.PAWN, from, to, white);

        // Remove the captured pawn (it's on the same rank as 'from', same file as 'to')
        int capturedSq = to + (white ? -8 : 8);
        removePiece(Piece.PAWN, capturedSq, !white);
    }

    private void makePromotionMove(int from, int to, Piece promoPiece, Piece captured) {
        // Remove the pawn
        removePiece(Piece.PAWN, from, white);

        // Remove captured piece if any
        if (captured != null) {
            removePiece(captured, to, !white);
        }

        // Place the promoted piece
        addPiece(promoPiece, to, white);
    }

    private void makeStandardMove(int from, int to, Piece piece, Piece captured) {
        // Remove captured piece if any
        if (captured != null) {
            removePiece(captured, to, !white);
        }
        // Move the piece
        movePiece(piece, from, to, white);
    }

    // ========================
    // Unmake Move
    // ========================

    /**
     * Reverts the last move made, restoring the board to its previous state.
     * Uses the saved state from the history stack.
     */
    public void unmakeMove() {
        white = !white;
        ply--;

        Move move = moveHistory[ply];
        int from = move.from();
        int to = move.to();

        // Read captured piece from current state (set during makeMove, after copy was taken)
        Piece captured = state.capturedPiece;
        // Restore the saved state
        BoardState savedState = stateHistory[ply];

        if (move.isCastling()) {
            unmakeCastlingMove(from, to);
        } else if (move.isPromotion()) {
            unmakePromotionMove(from, to, move.promoPiece(), captured);
        } else if (move.isEnPassant()) {
            unmakeEnPassantMove(from, to);
        } else {
            unmakeStandardMove(from, to, captured);
        }

        // Restore state, recycling the now-dead post-move object back to the pool.
        // The dead object is returned BEFORE `state` re-aliases the snapshot, so the
        // free-list never contains an active object (no self-copy on the next make).
        stateFreeList[stateFreeCount++] = state;
        state = savedState;

        // Restore full move number
        if (!white) {
            fullMoveNumber--;
        }
    }

    private void unmakeCastlingMove(int from, int to) {
        boolean kingside = Castling.isKingside(from, to);

        // Move king back
        movePieceRaw(Piece.KING, to, from, white);

        // Move rook back
        int rookFrom = Castling.rookFrom(kingside, white);
        int rookTo = Castling.rookTo(kingside, white);
        movePieceRaw(Piece.ROOK, rookTo, rookFrom, white);
    }

    private void unmakePromotionMove(int from, int to, Piece promoPiece, Piece captured) {
        // Remove the promoted piece
        removePieceRaw(promoPiece, to, white);

        // Restore the pawn
        addPieceRaw(Piece.PAWN, from, white);

        // Restore captured piece
        if (captured != null) {
            addPieceRaw(captured, to, !white);
        }
    }

    private void unmakeEnPassantMove(int from, int to) {
        // Move pawn back
        movePieceRaw(Piece.PAWN, to, from, white);

        // Restore the captured pawn
        int capturedSq = to + (white ? -8 : 8);
        addPieceRaw(Piece.PAWN, capturedSq, !white);
    }

    private void unmakeStandardMove(int from, int to, Piece captured) {
        Piece piece = mailbox[to];
        // Move piece back
        movePieceRaw(piece, to, from, white);

        // Restore captured piece
        if (captured != null) {
            addPieceRaw(captured, to, !white);
        }
    }

    /**
     * Makes a "null move" — passes the turn without moving any piece.
     * Used in Null Move Pruning (search optimization).
     */
    public void makeNullMove() {
        BoardState snap = stateFreeList[--stateFreeCount];
        state.copyInto(snap);
        stateHistory[ply] = snap;
        keyHistory[ply] = state.key;
        moveHistory[ply] = null;

        state.key ^= Zobrist.enPassantKey(state.enPassantSquare);
        state.enPassantSquare = Bits.Square.NONE;
        state.key ^= Zobrist.enPassantKey(state.enPassantSquare);
        state.key ^= Zobrist.sideKey();
        state.halfMoveClock++;

        ply++;
        white = !white;
    }

    /**
     * Unmakes a null move — restores the previous state.
     */
    public void unmakeNullMove() {
        white = !white;
        ply--;
        stateFreeList[stateFreeCount++] = state;
        state = stateHistory[ply];
    }

    // ========================
    // Raw piece ops (no Zobrist updates — used only in unmake where state is fully restored)
    // ========================

    private void addPieceRaw(Piece piece, int square, boolean isWhite) {
        int bbIndex = bbIndex(piece, isWhite);
        int occIndex = isWhite ? 12 : 13;
        bitboards[bbIndex] = Bits.setBit(bitboards[bbIndex], square);
        bitboards[occIndex] = Bits.setBit(bitboards[occIndex], square);
        mailbox[square] = piece;
        pieceColors[square] = isWhite;
    }

    private void removePieceRaw(Piece piece, int square, boolean isWhite) {
        int bbIndex = bbIndex(piece, isWhite);
        int occIndex = isWhite ? 12 : 13;
        bitboards[bbIndex] = Bits.clearBit(bitboards[bbIndex], square);
        bitboards[occIndex] = Bits.clearBit(bitboards[occIndex], square);
        mailbox[square] = null;
    }

    private void movePieceRaw(Piece piece, int from, int to, boolean isWhite) {
        int bbIndex = bbIndex(piece, isWhite);
        int occIndex = isWhite ? 12 : 13;
        long fromTo = Bits.toBitboard(from) | Bits.toBitboard(to);
        bitboards[bbIndex] ^= fromTo;
        bitboards[occIndex] ^= fromTo;
        mailbox[from] = null;
        mailbox[to] = piece;
        pieceColors[to] = isWhite;
    }

    // ========================
    // Bitboard Index Helper
    // ========================

    /**
     * Computes the bitboard array index for a given piece and color.
     * White pieces: 0-5, Black pieces: 6-11.
     */
    private int bbIndex(Piece piece, boolean isWhite) {
        return (isWhite ? 0 : Piece.COUNT) + piece.index();
    }

    // ========================
    // Accessors
    // ========================

    /** Returns the piece on the given square, or null if the square is empty. */
    public Piece pieceAt(int square) {
        return mailbox[square];
    }

    /** Returns the color of the piece on the given square. Only valid if pieceAt(square) != null. */
    public boolean isWhitePieceAt(int square) {
        return pieceColors[square];
    }

    /** Returns true if it's White's turn to move. */
    public boolean isWhiteToMove() {
        return white;
    }

    /** Returns the current ply (half-move count from root). */
    public int getPly() {
        return ply;
    }

    /** Returns the full move number (starts at 1). */
    public int getFullMoveNumber() {
        return fullMoveNumber;
    }

    /** Returns the current board state. */
    public BoardState getState() {
        return state;
    }

    /** Returns the Zobrist hash key for the current position. */
    public long key() {
        return state.getKey();
    }

    /** Returns the Zobrist hash key for the pawn structure. */
    public long pawnKey() {
        return state.getPawnKey();
    }

    // ========================
    // Bitboard Accessors
    // ========================

    /** Returns the bitboard for a specific piece type and color. */
    public long getBitboard(Piece piece, boolean isWhite) {
        return bitboards[bbIndex(piece, isWhite)];
    }

    /** Returns the bitboard of all White pieces (occupancy). */
    public long getWhitePieces() {
        return bitboards[12];
    }

    /** Returns the bitboard of all Black pieces (occupancy). */
    public long getBlackPieces() {
        return bitboards[13];
    }

    /** Returns the occupancy bitboard for the given color. */
    public long getPieces(boolean isWhite) {
        return isWhite ? bitboards[12] : bitboards[13];
    }

    /** Returns the combined occupancy bitboard (all pieces on the board). */
    public long getAllPieces() {
        return bitboards[12] | bitboards[13];
    }

    /** Returns the king square for the given color. */
    public int kingSquare(boolean isWhite) {
        return Bits.lsb(getBitboard(Piece.KING, isWhite));
    }

    // ========================
    // Repetition Detection
    // ========================

    /**
     * Checks if the given side has any material other than pawns and king.
     * Used for Null Move Pruning.
     */
    public boolean hasNonPawnMaterial(boolean isWhite) {
        long knights = getBitboard(Piece.KNIGHT, isWhite);
        long bishops = getBitboard(Piece.BISHOP, isWhite);
        long rooks = getBitboard(Piece.ROOK, isWhite);
        long queens = getBitboard(Piece.QUEEN, isWhite);
        return (knights | bishops | rooks | queens) != 0;
    }

    /**
     * Checks if the current position has occurred before (for repetition draw detection).
     * Only looks back within the current game (not across newgame boundaries).
     *
     * @return true if this position is a repetition
     */
    public boolean isRepetition() {
        long currentKey = state.key;
        // Only need to look back halfMoveClock moves (since last irreversible move)
        int limit = Math.min(state.halfMoveClock, ply);
        // Step back 2 at a time (same side to move)
        for (int i = 2; i <= limit; i += 2) {
            if (keyHistory[ply - i] == currentKey) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the 50-move rule is met (100 half-moves without pawn move or capture).
     */
    public boolean isFiftyMoveRule() {
        return state.halfMoveClock >= 100;
    }

    /**
     * Checks for insufficient material draw (K vs K, K+B vs K, K+N vs K).
     */
    public boolean isInsufficientMaterial() {
        // If any pawns, rooks, or queens exist, there's sufficient material
        if (getBitboard(Piece.PAWN, true) != 0 || getBitboard(Piece.PAWN, false) != 0) return false;
        if (getBitboard(Piece.ROOK, true) != 0 || getBitboard(Piece.ROOK, false) != 0) return false;
        if (getBitboard(Piece.QUEEN, true) != 0 || getBitboard(Piece.QUEEN, false) != 0) return false;

        int whiteKnights = Bits.popCount(getBitboard(Piece.KNIGHT, true));
        int blackKnights = Bits.popCount(getBitboard(Piece.KNIGHT, false));
        int whiteBishops = Bits.popCount(getBitboard(Piece.BISHOP, true));
        int blackBishops = Bits.popCount(getBitboard(Piece.BISHOP, false));
        int totalMinor = whiteKnights + blackKnights + whiteBishops + blackBishops;

        // K vs K
        if (totalMinor == 0) return true;
        // K+B vs K or K+N vs K
        if (totalMinor == 1) return true;

        return false;
    }

    // ========================
    // Setters (for FEN parser)
    // ========================

    public void setWhiteToMove(boolean white) {
        this.white = white;
    }

    public void setState(BoardState state) {
        this.state = state;
    }

    public void setFenState(int castlingRights, int enPassantSquare, int halfMoveClock) {
        this.state = new BoardState(castlingRights, enPassantSquare, halfMoveClock);
    }

    public void finalizeSetup() {
        this.state.key = Zobrist.computeKey(this);
        this.state.pawnKey = Zobrist.computePawnKey(this);
    }

    public void setFullMoveNumber(int fullMoveNumber) {
        this.fullMoveNumber = fullMoveNumber;
    }

    public void setPly(int ply) {
        this.ply = ply;
    }

    // ========================
    // Starting Position Setup
    // ========================

    private void setupStartingPosition() {
        // White pieces
        addPiece(Piece.ROOK,   Bits.Square.A1, true);
        addPiece(Piece.KNIGHT, Bits.Square.B1, true);
        addPiece(Piece.BISHOP, Bits.Square.C1, true);
        addPiece(Piece.QUEEN,  Bits.Square.D1, true);
        addPiece(Piece.KING,   Bits.Square.E1, true);
        addPiece(Piece.BISHOP, Bits.Square.F1, true);
        addPiece(Piece.KNIGHT, Bits.Square.G1, true);
        addPiece(Piece.ROOK,   Bits.Square.H1, true);
        for (int file = 0; file < 8; file++) {
            addPiece(Piece.PAWN, Bits.toSquare(file, 1), true);
        }

        // Black pieces
        addPiece(Piece.ROOK,   Bits.Square.A8, false);
        addPiece(Piece.KNIGHT, Bits.Square.B8, false);
        addPiece(Piece.BISHOP, Bits.Square.C8, false);
        addPiece(Piece.QUEEN,  Bits.Square.D8, false);
        addPiece(Piece.KING,   Bits.Square.E8, false);
        addPiece(Piece.BISHOP, Bits.Square.F8, false);
        addPiece(Piece.KNIGHT, Bits.Square.G8, false);
        addPiece(Piece.ROOK,   Bits.Square.H8, false);
        for (int file = 0; file < 8; file++) {
            addPiece(Piece.PAWN, Bits.toSquare(file, 6), false);
        }

        state.castlingRights = BoardState.ALL_CASTLING;
        state.enPassantSquare = Bits.Square.NONE;
        state.halfMoveClock = 0;
        // Recompute the castling key since we set rights after pieces were added
        state.key ^= Zobrist.castlingKey(state.castlingRights);
    }

    // ========================
    // Display
    // ========================

    /**
     * Returns a human-readable string representation of the board.
     * Shows the board from White's perspective (rank 8 at top).
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (int rank = 7; rank >= 0; rank--) {
            sb.append("  ").append(rank + 1).append(" | ");
            for (int file = 0; file < 8; file++) {
                int sq = Bits.toSquare(file, rank);
                Piece piece = mailbox[sq];
                if (piece == null) {
                    sb.append(". ");
                } else {
                    sb.append(piece.toChar(pieceColors[sq])).append(' ');
                }
            }
            sb.append('\n');
        }
        sb.append("      ----------------\n");
        sb.append("      a b c d e f g h\n");
        sb.append("\n  Side to move: ").append(white ? "White" : "Black");
        sb.append("\n  Castling: ").append(castlingString());
        sb.append("\n  En passant: ").append(
                state.enPassantSquare == Bits.Square.NONE ? "-" : Bits.toAlgebraic(state.enPassantSquare));
        sb.append("\n  Half-move clock: ").append(state.halfMoveClock);
        sb.append("\n  Full move: ").append(fullMoveNumber);
        sb.append("\n  Key: ").append(String.format("%016x", state.key));
        sb.append("\n");
        return sb.toString();
    }

    private String castlingString() {
        if (state.castlingRights == BoardState.NO_CASTLING) return "-";
        StringBuilder sb = new StringBuilder();
        if (state.hasCastlingRight(BoardState.WHITE_KING_SIDE)) sb.append('K');
        if (state.hasCastlingRight(BoardState.WHITE_QUEEN_SIDE)) sb.append('Q');
        if (state.hasCastlingRight(BoardState.BLACK_KING_SIDE)) sb.append('k');
        if (state.hasCastlingRight(BoardState.BLACK_QUEEN_SIDE)) sb.append('q');
        return sb.toString();
    }
}



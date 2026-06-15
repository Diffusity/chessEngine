package com.titan.board;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the board representation layer.
 * Tests cover Piece/Color enums, Bits utility, Move encoding, Board state, and make/unmake.
 */
class BoardTest {

    // ========================
    // Piece Enum Tests
    // ========================

    @Test
    @DisplayName("Piece enum - index values are sequential 0-5")
    void testPieceIndices() {
        assertEquals(0, Piece.PAWN.index());
        assertEquals(1, Piece.KNIGHT.index());
        assertEquals(2, Piece.BISHOP.index());
        assertEquals(3, Piece.ROOK.index());
        assertEquals(4, Piece.QUEEN.index());
        assertEquals(5, Piece.KING.index());
    }

    @Test
    @DisplayName("Piece enum - fromChar parses all valid characters")
    void testPieceFromChar() {
        assertEquals(Piece.PAWN, Piece.fromChar('P'));
        assertEquals(Piece.PAWN, Piece.fromChar('p'));
        assertEquals(Piece.KNIGHT, Piece.fromChar('N'));
        assertEquals(Piece.QUEEN, Piece.fromChar('q'));
        assertEquals(Piece.KING, Piece.fromChar('K'));
    }

    @Test
    @DisplayName("Piece enum - fromChar throws on invalid character")
    void testPieceFromCharInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Piece.fromChar('X'));
    }

    @Test
    @DisplayName("Piece enum - toChar respects color")
    void testPieceToChar() {
        assertEquals('P', Piece.PAWN.toChar(true));
        assertEquals('p', Piece.PAWN.toChar(false));
        assertEquals('K', Piece.KING.toChar(true));
        assertEquals('k', Piece.KING.toChar(false));
    }

    @Test
    @DisplayName("Piece enum - fromIndex roundtrips with index()")
    void testPieceFromIndex() {
        for (Piece p : Piece.values()) {
            assertEquals(p, Piece.fromIndex(p.index()));
        }
    }

    // ========================
    // Color Enum Tests
    // ========================

    @Test
    @DisplayName("Color - opposite returns the other color")
    void testColorOpposite() {
        assertEquals(Color.BLACK, Color.WHITE.opposite());
        assertEquals(Color.WHITE, Color.BLACK.opposite());
    }

    @Test
    @DisplayName("Color - pawn direction is +8 for White, -8 for Black")
    void testColorPawnDirection() {
        assertEquals(8, Color.WHITE.pawnDirection());
        assertEquals(-8, Color.BLACK.pawnDirection());
    }

    // ========================
    // Bits Utility Tests
    // ========================

    @Test
    @DisplayName("Bits - fileOf and rankOf for corner squares")
    void testBitsFileRank() {
        assertEquals(0, Bits.fileOf(Bits.Square.A1)); // a-file
        assertEquals(0, Bits.rankOf(Bits.Square.A1)); // rank 1
        assertEquals(7, Bits.fileOf(Bits.Square.H8)); // h-file
        assertEquals(7, Bits.rankOf(Bits.Square.H8)); // rank 8
        assertEquals(4, Bits.fileOf(Bits.Square.E4)); // e-file
        assertEquals(3, Bits.rankOf(Bits.Square.E4)); // rank 4
    }

    @Test
    @DisplayName("Bits - toSquare is inverse of fileOf/rankOf")
    void testBitsToSquare() {
        for (int sq = 0; sq < 64; sq++) {
            assertEquals(sq, Bits.toSquare(Bits.fileOf(sq), Bits.rankOf(sq)));
        }
    }

    @Test
    @DisplayName("Bits - algebraic notation roundtrips")
    void testBitsAlgebraic() {
        assertEquals("a1", Bits.toAlgebraic(Bits.Square.A1));
        assertEquals("h8", Bits.toAlgebraic(Bits.Square.H8));
        assertEquals("e4", Bits.toAlgebraic(Bits.Square.E4));
        assertEquals(Bits.Square.A1, Bits.fromAlgebraic("a1"));
        assertEquals(Bits.Square.H8, Bits.fromAlgebraic("h8"));
        assertEquals(Bits.Square.E4, Bits.fromAlgebraic("e4"));
    }

    @Test
    @DisplayName("Bits - setBit and hasBit")
    void testBitsSetAndHas() {
        long bb = 0L;
        bb = Bits.setBit(bb, 0);
        assertTrue(Bits.hasBit(bb, 0));
        assertFalse(Bits.hasBit(bb, 1));

        bb = Bits.setBit(bb, 63);
        assertTrue(Bits.hasBit(bb, 63));
    }

    @Test
    @DisplayName("Bits - clearBit removes a set bit")
    void testBitsClear() {
        long bb = Bits.setBit(0L, 10);
        assertTrue(Bits.hasBit(bb, 10));
        bb = Bits.clearBit(bb, 10);
        assertFalse(Bits.hasBit(bb, 10));
    }

    @Test
    @DisplayName("Bits - lsb returns lowest set bit")
    void testBitsLsb() {
        long bb = Bits.setBit(0L, 5);
        bb = Bits.setBit(bb, 20);
        bb = Bits.setBit(bb, 50);
        assertEquals(5, Bits.lsb(bb));
    }

    @Test
    @DisplayName("Bits - popCount returns number of set bits")
    void testBitsPopCount() {
        long bb = 0L;
        assertEquals(0, Bits.popCount(bb));
        bb = Bits.setBit(bb, 0);
        bb = Bits.setBit(bb, 31);
        bb = Bits.setBit(bb, 63);
        assertEquals(3, Bits.popCount(bb));
    }

    // ========================
    // Move Encoding Tests
    // ========================

    @Test
    @DisplayName("Move - quiet move encodes/decodes from and to squares")
    void testMoveQuiet() {
        Move move = Move.ofQuiet(Bits.Square.E2, Bits.Square.E4);
        assertEquals(Bits.Square.E2, move.from());
        assertEquals(Bits.Square.E4, move.to());
        assertEquals(Move.FLAG_QUIET, move.flags());
        assertFalse(move.isCapture());
        assertFalse(move.isCastling());
        assertFalse(move.isPromotion());
        assertTrue(move.isQuiet());
    }

    @Test
    @DisplayName("Move - capture move has correct flag")
    void testMoveCapture() {
        Move move = Move.ofCapture(Bits.Square.D4, Bits.Square.E5);
        assertTrue(move.isCapture());
        assertFalse(move.isQuiet());
    }

    @Test
    @DisplayName("Move - castling move flags")
    void testMoveCastling() {
        Move kingSide = Move.of(Bits.Square.E1, Bits.Square.G1, Move.FLAG_CASTLE_KING);
        assertTrue(kingSide.isCastling());
        assertFalse(kingSide.isCapture());

        Move queenSide = Move.of(Bits.Square.E1, Bits.Square.C1, Move.FLAG_CASTLE_QUEEN);
        assertTrue(queenSide.isCastling());
    }

    @Test
    @DisplayName("Move - promotion to queen")
    void testMovePromotion() {
        Move promo = Move.of(Bits.Square.E7, Bits.Square.E8, Move.FLAG_PROMO_QUEEN);
        assertTrue(promo.isPromotion());
        assertEquals(Piece.QUEEN, promo.promoPiece());
        assertFalse(promo.isCapture());
    }

    @Test
    @DisplayName("Move - promotion with capture")
    void testMovePromotionCapture() {
        Move promo = Move.of(Bits.Square.E7, Bits.Square.D8, Move.FLAG_PROMO_QUEEN_CAP);
        assertTrue(promo.isPromotion());
        assertTrue(promo.isCapture());
        assertEquals(Piece.QUEEN, promo.promoPiece());
    }

    @Test
    @DisplayName("Move - en passant flag")
    void testMoveEnPassant() {
        Move ep = Move.of(Bits.Square.E5, Bits.Square.D6, Move.FLAG_EP_CAPTURE);
        assertTrue(ep.isEnPassant());
        assertTrue(ep.isCapture());
    }

    @Test
    @DisplayName("Move - UCI notation roundtrip")
    void testMoveUCI() {
        Move move = Move.ofQuiet(Bits.Square.E2, Bits.Square.E4);
        assertEquals("e2e4", move.toUCI());

        Move promo = Move.of(Bits.Square.A7, Bits.Square.A8, Move.FLAG_PROMO_QUEEN);
        assertEquals("a7a8q", promo.toUCI());
    }

    @Test
    @DisplayName("Move - equals and hashCode")
    void testMoveEquality() {
        Move m1 = Move.ofQuiet(Bits.Square.E2, Bits.Square.E4);
        Move m2 = Move.ofQuiet(Bits.Square.E2, Bits.Square.E4);
        Move m3 = Move.ofCapture(Bits.Square.E2, Bits.Square.E4);

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1, m3); // Different flags
    }

    // ========================
    // Board Tests
    // ========================

    @Test
    @DisplayName("Board - starting position has 32 pieces")
    void testStartingPositionPieceCount() {
        Board board = Board.startingPosition();
        assertEquals(16, Bits.popCount(board.getWhitePieces()));
        assertEquals(16, Bits.popCount(board.getBlackPieces()));
        assertEquals(32, Bits.popCount(board.getAllPieces()));
    }

    @Test
    @DisplayName("Board - starting position piece placement")
    void testStartingPositionPieces() {
        Board board = Board.startingPosition();

        // White king on e1
        assertEquals(Piece.KING, board.pieceAt(Bits.Square.E1));
        assertTrue(board.isWhitePieceAt(Bits.Square.E1));

        // Black queen on d8
        assertEquals(Piece.QUEEN, board.pieceAt(Bits.Square.D8));
        assertFalse(board.isWhitePieceAt(Bits.Square.D8));

        // White pawn on e2
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.E2));

        // Empty square on e4
        assertNull(board.pieceAt(Bits.Square.E4));
    }

    @Test
    @DisplayName("Board - starting position is White to move")
    void testStartingPositionSideToMove() {
        Board board = Board.startingPosition();
        assertTrue(board.isWhiteToMove());
    }

    @Test
    @DisplayName("Board - starting position has all castling rights")
    void testStartingPositionCastling() {
        Board board = Board.startingPosition();
        BoardState state = board.getState();
        assertTrue(state.hasCastlingRight(BoardState.WHITE_KING_SIDE));
        assertTrue(state.hasCastlingRight(BoardState.WHITE_QUEEN_SIDE));
        assertTrue(state.hasCastlingRight(BoardState.BLACK_KING_SIDE));
        assertTrue(state.hasCastlingRight(BoardState.BLACK_QUEEN_SIDE));
    }

    @Test
    @DisplayName("Board - make quiet move updates board correctly")
    void testMakeQuietMove() {
        Board board = Board.startingPosition();
        long keyBefore = board.key();

        // 1. e2-e4 (pawn double push)
        Move e2e4 = Move.of(Bits.Square.E2, Bits.Square.E4, Move.FLAG_PAWN_DOUBLE);
        assertTrue(board.makeMove(e2e4));

        // Pawn should be on e4 now
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.E4));
        assertNull(board.pieceAt(Bits.Square.E2));

        // Side to move should flip
        assertFalse(board.isWhiteToMove());

        // En passant square should be e3
        assertEquals(Bits.Square.E3, board.getState().getEnPassantSquare());

        // Key should have changed
        assertNotEquals(keyBefore, board.key());
    }

    @Test
    @DisplayName("Board - unmake move restores board state")
    void testUnmakeMove() {
        Board board = Board.startingPosition();
        long keyBefore = board.key();

        Move e2e4 = Move.of(Bits.Square.E2, Bits.Square.E4, Move.FLAG_PAWN_DOUBLE);
        board.makeMove(e2e4);
        board.unmakeMove();

        // Board should be back to starting position
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.E2));
        assertNull(board.pieceAt(Bits.Square.E4));
        assertTrue(board.isWhiteToMove());
        assertEquals(keyBefore, board.key());
    }

    @Test
    @DisplayName("Board - make/unmake multiple moves preserves key")
    void testMakeUnmakeMultipleMoves() {
        Board board = Board.startingPosition();
        long keyBefore = board.key();

        // 1. e4 e5 2. Nf3
        Move e4 = Move.of(Bits.Square.E2, Bits.Square.E4, Move.FLAG_PAWN_DOUBLE);
        Move e5 = Move.of(Bits.Square.E7, Bits.Square.E5, Move.FLAG_PAWN_DOUBLE);
        Move nf3 = Move.ofQuiet(Bits.Square.G1, Bits.Square.F3);

        board.makeMove(e4);
        board.makeMove(e5);
        board.makeMove(nf3);

        // Unmake all three
        board.unmakeMove();
        board.unmakeMove();
        board.unmakeMove();

        assertEquals(keyBefore, board.key());
        assertTrue(board.isWhiteToMove());
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.E2));
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.E7));
        assertEquals(Piece.KNIGHT, board.pieceAt(Bits.Square.G1));
    }

    @Test
    @DisplayName("Board - capture move removes captured piece")
    void testCaptureMove() {
        Board board = Board.startingPosition();

        // Set up: 1. e4 d5 2. exd5
        board.makeMove(Move.of(Bits.Square.E2, Bits.Square.E4, Move.FLAG_PAWN_DOUBLE));
        board.makeMove(Move.of(Bits.Square.D7, Bits.Square.D5, Move.FLAG_PAWN_DOUBLE));

        long keyBefore = board.key();
        Move capture = Move.ofCapture(Bits.Square.E4, Bits.Square.D5);
        board.makeMove(capture);

        // White pawn should be on d5, no black pawn
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.D5));
        assertTrue(board.isWhitePieceAt(Bits.Square.D5));
        assertNull(board.pieceAt(Bits.Square.E4));

        // Unmake should restore
        board.unmakeMove();
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.E4));
        assertEquals(Piece.PAWN, board.pieceAt(Bits.Square.D5));
        assertFalse(board.isWhitePieceAt(Bits.Square.D5)); // Black pawn
        assertEquals(keyBefore, board.key());
    }

    @Test
    @DisplayName("Board - castling move moves both king and rook")
    void testCastlingMove() {
        Board board = new Board();
        // Place only king and rook for white kingside castling
        board.addPiece(Piece.KING, Bits.Square.E1, true);
        board.addPiece(Piece.ROOK, Bits.Square.H1, true);
        board.addPiece(Piece.KING, Bits.Square.E8, false);
        board.getState().castlingRights = BoardState.ALL_CASTLING;

        Move castle = Move.of(Bits.Square.E1, Bits.Square.G1, Move.FLAG_CASTLE_KING);
        board.makeMove(castle);

        assertEquals(Piece.KING, board.pieceAt(Bits.Square.G1));
        assertEquals(Piece.ROOK, board.pieceAt(Bits.Square.F1));
        assertNull(board.pieceAt(Bits.Square.E1));
        assertNull(board.pieceAt(Bits.Square.H1));
    }

    @Test
    @DisplayName("Board - king square returns correct square")
    void testKingSquare() {
        Board board = Board.startingPosition();
        assertEquals(Bits.Square.E1, board.kingSquare(true));
        assertEquals(Bits.Square.E8, board.kingSquare(false));
    }

    @Test
    @DisplayName("Board - Zobrist key is non-zero for starting position")
    void testZobristKeyNonZero() {
        Board board = Board.startingPosition();
        assertNotEquals(0L, board.key());
    }

    @Test
    @DisplayName("Board - insufficient material detection")
    void testInsufficientMaterial() {
        // K vs K
        Board board = new Board();
        board.addPiece(Piece.KING, Bits.Square.E1, true);
        board.addPiece(Piece.KING, Bits.Square.E8, false);
        assertTrue(board.isInsufficientMaterial());

        // K+N vs K
        Board board2 = new Board();
        board2.addPiece(Piece.KING, Bits.Square.E1, true);
        board2.addPiece(Piece.KNIGHT, Bits.Square.B1, true);
        board2.addPiece(Piece.KING, Bits.Square.E8, false);
        assertTrue(board2.isInsufficientMaterial());

        // K+R vs K — sufficient
        Board board3 = new Board();
        board3.addPiece(Piece.KING, Bits.Square.E1, true);
        board3.addPiece(Piece.ROOK, Bits.Square.A1, true);
        board3.addPiece(Piece.KING, Bits.Square.E8, false);
        assertFalse(board3.isInsufficientMaterial());
    }
}

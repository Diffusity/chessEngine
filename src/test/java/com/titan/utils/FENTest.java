package com.titan.utils;

import com.titan.board.Bits;
import com.titan.board.Board;
import com.titan.board.BoardState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FENTest {

    @Test
    public void testStartingPosition() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        
        assertTrue(board.isWhiteToMove());
        assertEquals(BoardState.ALL_CASTLING, board.getState().getCastlingRights());
        assertEquals(Bits.Square.NONE, board.getState().getEnPassantSquare());
        assertEquals(0, board.getState().getHalfMoveClock());
        assertEquals(1, board.getFullMoveNumber());
        
        String generated = FEN.generate(board);
        assertEquals(FEN.STARTING_FEN, generated);
    }

    @Test
    public void testKiwipete() {
        String kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        Board board = FEN.parse(kiwipete);
        
        assertTrue(board.isWhiteToMove());
        assertEquals(BoardState.ALL_CASTLING, board.getState().getCastlingRights());
        assertEquals(Bits.Square.NONE, board.getState().getEnPassantSquare());
        assertEquals(0, board.getState().getHalfMoveClock());
        assertEquals(1, board.getFullMoveNumber());
        
        String generated = FEN.generate(board);
        assertEquals(kiwipete, generated);
    }

    @Test
    public void testEnPassantAndCastling() {
        String fen = "rnbqkbnr/pppp1ppp/8/3Pp3/8/8/PPP1PPPP/RNBQKBNR w KQkq e6 0 2";
        Board board = FEN.parse(fen);
        
        assertTrue(board.isWhiteToMove());
        assertEquals(BoardState.ALL_CASTLING, board.getState().getCastlingRights());
        assertEquals(Bits.fromAlgebraic("e6"), board.getState().getEnPassantSquare());
        assertEquals(0, board.getState().getHalfMoveClock());
        assertEquals(2, board.getFullMoveNumber());
        
        String generated = FEN.generate(board);
        assertEquals(fen, generated);
    }

    @Test
    public void testBlackToMoveNoCastling() {
        String fen = "8/8/8/8/8/8/8/4K2k b - - 50 120";
        Board board = FEN.parse(fen);
        
        assertFalse(board.isWhiteToMove());
        assertEquals(BoardState.NO_CASTLING, board.getState().getCastlingRights());
        assertEquals(Bits.Square.NONE, board.getState().getEnPassantSquare());
        assertEquals(50, board.getState().getHalfMoveClock());
        assertEquals(120, board.getFullMoveNumber());
        
        String generated = FEN.generate(board);
        assertEquals(fen, generated);
    }
}

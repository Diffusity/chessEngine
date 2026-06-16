package com.titan.utils;

import com.titan.board.Bits;
import com.titan.board.Board;
import com.titan.board.BoardState;
import com.titan.board.Piece;

public class FEN {

    public static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static Board parse(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid FEN: " + fen);
        }

        Board board = new Board();

        // 1. Piece placement
        String placement = parts[0];
        int rank = 7;
        int file = 0;

        for (int i = 0; i < placement.length(); i++) {
            char c = placement.charAt(i);
            if (c == '/') {
                rank--;
                file = 0;
            } else if (Character.isDigit(c)) {
                file += Character.getNumericValue(c);
            } else {
                boolean isWhite = Character.isUpperCase(c);
                Piece piece = Piece.fromChar(c);
                int sq = Bits.toSquare(file, rank);
                board.addPiece(piece, sq, isWhite);
                file++;
            }
        }

        // 2. Active color
        boolean isWhite = parts[1].equals("w");
        board.setWhiteToMove(isWhite);

        // 3. Castling availability
        int castlingRights = BoardState.NO_CASTLING;
        String castling = parts[2];
        if (!castling.equals("-")) {
            if (castling.indexOf('K') != -1) castlingRights |= BoardState.WHITE_KING_SIDE;
            if (castling.indexOf('Q') != -1) castlingRights |= BoardState.WHITE_QUEEN_SIDE;
            if (castling.indexOf('k') != -1) castlingRights |= BoardState.BLACK_KING_SIDE;
            if (castling.indexOf('q') != -1) castlingRights |= BoardState.BLACK_QUEEN_SIDE;
        }

        // 4. En passant target square
        int epSquare = Bits.Square.NONE;
        String ep = parts[3];
        if (!ep.equals("-")) {
            epSquare = Bits.fromAlgebraic(ep);
        }

        // 5. Halfmove clock
        int halfMoveClock = 0;
        if (parts.length > 4) {
            halfMoveClock = Integer.parseInt(parts[4]);
        }

        // Apply state
        board.setFenState(castlingRights, epSquare, halfMoveClock);

        // 6. Fullmove number
        if (parts.length > 5) {
            board.setFullMoveNumber(Integer.parseInt(parts[5]));
        } else {
            board.setFullMoveNumber(1);
        }

        board.setPly(0); // Assuming FEN is root

        // Finalize Zobrist keys based on the setup
        board.finalizeSetup();

        return board;
    }

    public static String generate(Board board) {
        StringBuilder sb = new StringBuilder();

        // 1. Piece placement
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int sq = Bits.toSquare(file, rank);
                Piece piece = board.pieceAt(sq);
                if (piece == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(piece.toChar(board.isWhitePieceAt(sq)));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }

        // 2. Active color
        sb.append(' ').append(board.isWhiteToMove() ? 'w' : 'b');

        // 3. Castling availability
        sb.append(' ');
        BoardState state = board.getState();
        int castling = state.getCastlingRights();
        if (castling == BoardState.NO_CASTLING) {
            sb.append('-');
        } else {
            if ((castling & BoardState.WHITE_KING_SIDE) != 0) sb.append('K');
            if ((castling & BoardState.WHITE_QUEEN_SIDE) != 0) sb.append('Q');
            if ((castling & BoardState.BLACK_KING_SIDE) != 0) sb.append('k');
            if ((castling & BoardState.BLACK_QUEEN_SIDE) != 0) sb.append('q');
        }

        // 4. En passant target square
        sb.append(' ');
        int ep = state.getEnPassantSquare();
        if (ep == Bits.Square.NONE) {
            sb.append('-');
        } else {
            sb.append(Bits.toAlgebraic(ep));
        }

        // 5. Halfmove clock
        sb.append(' ').append(state.getHalfMoveClock());

        // 6. Fullmove number
        sb.append(' ').append(board.getFullMoveNumber());

        return sb.toString();
    }
}

package com.titan.movegen;

import com.titan.board.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Move generation for the Titan Chess Engine.
 * <p>
 * Generates pseudo-legal moves using bitboards and pre-computed attack tables,
 * and filters them to strictly legal moves by making the move on the board and
 * verifying the king is not in check.
 */
public class MoveGenerator {

    /**
     * Generates all strictly legal moves for the current position.
     *
     * @param board the board position
     * @return a list of all legal moves
     */
    public static List<Move> generateLegalMoves(Board board) {
        List<Move> pseudoLegal = generatePseudoLegalMoves(board);
        List<Move> legalMoves = new ArrayList<>(pseudoLegal.size());
        
        boolean whiteToMove = board.isWhiteToMove();

        for (Move move : pseudoLegal) {
            // Apply the move
            if (board.makeMove(move)) {
                // If making the move doesn't leave our king in check, it's legal
                if (!isInCheck(board, whiteToMove)) {
                    legalMoves.add(move);
                }
                // Always unmake
                board.unmakeMove();
            }
        }
        
        return legalMoves;
    }

    /**
     * Generates all strictly legal capture moves for the current position.
     * Used by quiescence search to resolve tactical sequences.
     */
    public static List<Move> generateCaptures(Board board) {
        List<Move> pseudoLegal = generatePseudoLegalMoves(board);
        List<Move> captures = new ArrayList<>();
        boolean whiteToMove = board.isWhiteToMove();

        for (Move move : pseudoLegal) {
            if (!move.isCapture()) continue;
            if (board.makeMove(move)) {
                if (!isInCheck(board, whiteToMove)) {
                    captures.add(move);
                }
                board.unmakeMove();
            }
        }
        return captures;
    }

    /**
     * Checks if the given side's king is currently under attack.
     *
     * @param board the board position
     * @param white true to check White's king, false for Black's king
     * @return true if the king is attacked (in check)
     */
    public static boolean isInCheck(Board board, boolean white) {
        int kingSq = board.kingSquare(white);
        return isAttacked(board, kingSq, !white);
    }

    /**
     * Determines if a specific square is attacked by any piece of the given color.
     *
     * @param board   the board position
     * @param sq      the target square (0-63)
     * @param byWhite true to check if attacked by White, false for Black
     * @return true if the square is attacked
     */
    public static boolean isAttacked(Board board, int sq, boolean byWhite) {
        long occupied = board.getAllPieces();

        // 1. Attacked by pawns?
        long enemyPawns = board.getBitboard(Piece.PAWN, byWhite);
        // A square is attacked by a pawn if the *reverse* pawn attack from the square hits a pawn
        long pawnAttacksFromSq = Attacks.PAWN_ATTACKS[byWhite ? 1 : 0][sq]; 
        if ((enemyPawns & pawnAttacksFromSq) != 0) return true;

        // 2. Attacked by knights?
        long enemyKnights = board.getBitboard(Piece.KNIGHT, byWhite);
        if ((enemyKnights & Attacks.KNIGHT_ATTACKS[sq]) != 0) return true;

        // 3. Attacked by king?
        long enemyKing = board.getBitboard(Piece.KING, byWhite);
        if ((enemyKing & Attacks.KING_ATTACKS[sq]) != 0) return true;

        // 4. Attacked by bishops or queens?
        long enemyBishopsQueens = board.getBitboard(Piece.BISHOP, byWhite) | board.getBitboard(Piece.QUEEN, byWhite);
        if (enemyBishopsQueens != 0) {
            long bishopAttacks = Attacks.bishopAttacks(sq, occupied);
            if ((enemyBishopsQueens & bishopAttacks) != 0) return true;
        }

        // 5. Attacked by rooks or queens?
        long enemyRooksQueens = board.getBitboard(Piece.ROOK, byWhite) | board.getBitboard(Piece.QUEEN, byWhite);
        if (enemyRooksQueens != 0) {
            long rookAttacks = Attacks.rookAttacks(sq, occupied);
            if ((enemyRooksQueens & rookAttacks) != 0) return true;
        }

        return false;
    }

    /**
     * Generates all pseudo-legal moves. A move is pseudo-legal if it follows the
     * movement rules of the piece, but it might leave the king in check.
     */
    public static List<Move> generatePseudoLegalMoves(Board board) {
        List<Move> moves = new ArrayList<>(40);
        boolean white = board.isWhiteToMove();
        
        long friendlyPieces = board.getPieces(white);
        long enemyPieces = board.getPieces(!white);
        long allPieces = friendlyPieces | enemyPieces;
        long emptySquares = ~allPieces;

        generatePawnMoves(board, white, enemyPieces, emptySquares, moves);
        generateKnightMoves(board, white, friendlyPieces, enemyPieces, moves);
        generateKingMoves(board, white, friendlyPieces, enemyPieces, moves);
        generateSlidingMoves(board, white, friendlyPieces, enemyPieces, allPieces, moves);

        return moves;
    }

    private static void generatePawnMoves(Board board, boolean white, long enemyPieces, long emptySquares, List<Move> moves) {
        long pawns = board.getBitboard(Piece.PAWN, white);
        int pushOffset = white ? 8 : -8;
        long promoRankMask = white ? Bits.Rank.WHITE_PROMOTION : Bits.Rank.BLACK_PROMOTION;
        long startRankMask = white ? Bits.Rank.WHITE_PAWN_START : Bits.Rank.BLACK_PAWN_START;

        // Single push
        long singlePushes = white ? (pawns << 8) & emptySquares : (pawns >>> 8) & emptySquares;
        long singlePushesPromo = singlePushes & promoRankMask;
        long singlePushesNormal = singlePushes & ~promoRankMask;

        // Double push
        long doublePushes = white ? (singlePushesNormal << 8) & emptySquares : (singlePushesNormal >>> 8) & emptySquares;
        doublePushes &= white ? Bits.Rank._4 : Bits.Rank._5; // Only allowed to rank 4 or 5

        addPawnMoves(singlePushesNormal, -pushOffset, Move.FLAG_QUIET, moves);
        addPawnMoves(doublePushes, -(pushOffset * 2), Move.FLAG_PAWN_DOUBLE, moves);
        addPawnPromotions(singlePushesPromo, -pushOffset, false, moves);

        // Captures
        long pawnsLeft = pawns;
        while (pawnsLeft != 0) {
            int from = Bits.lsb(pawnsLeft);
            long attacks = Attacks.PAWN_ATTACKS[white ? 0 : 1][from];
            
            // Standard captures
            long captures = attacks & enemyPieces;
            while (captures != 0) {
                int to = Bits.lsb(captures);
                if ((Bits.toBitboard(to) & promoRankMask) != 0) {
                    // Promotion with capture
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_QUEEN_CAP));
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_ROOK_CAP));
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_BISHOP_CAP));
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_KNIGHT_CAP));
                } else {
                    moves.add(Move.ofCapture(from, to));
                }
                captures &= captures - 1; // clear LSB
            }

            // En passant
            int epSq = board.getState().getEnPassantSquare();
            if (epSq != Bits.Square.NONE && (attacks & Bits.toBitboard(epSq)) != 0) {
                moves.add(Move.of(from, epSq, Move.FLAG_EP_CAPTURE));
            }

            pawnsLeft &= pawnsLeft - 1; // clear LSB
        }
    }

    private static void addPawnMoves(long bitboard, int fromOffset, int flag, List<Move> moves) {
        while (bitboard != 0) {
            int to = Bits.lsb(bitboard);
            int from = to + fromOffset;
            moves.add(Move.of(from, to, flag));
            bitboard &= bitboard - 1;
        }
    }

    private static void addPawnPromotions(long bitboard, int fromOffset, boolean isCapture, List<Move> moves) {
        while (bitboard != 0) {
            int to = Bits.lsb(bitboard);
            int from = to + fromOffset;
            if (isCapture) {
                moves.add(Move.of(from, to, Move.FLAG_PROMO_QUEEN_CAP));
                moves.add(Move.of(from, to, Move.FLAG_PROMO_ROOK_CAP));
                moves.add(Move.of(from, to, Move.FLAG_PROMO_BISHOP_CAP));
                moves.add(Move.of(from, to, Move.FLAG_PROMO_KNIGHT_CAP));
            } else {
                moves.add(Move.of(from, to, Move.FLAG_PROMO_QUEEN));
                moves.add(Move.of(from, to, Move.FLAG_PROMO_ROOK));
                moves.add(Move.of(from, to, Move.FLAG_PROMO_BISHOP));
                moves.add(Move.of(from, to, Move.FLAG_PROMO_KNIGHT));
            }
            bitboard &= bitboard - 1;
        }
    }

    private static void generateKnightMoves(Board board, boolean white, long friendly, long enemy, List<Move> moves) {
        long knights = board.getBitboard(Piece.KNIGHT, white);
        while (knights != 0) {
            int from = Bits.lsb(knights);
            long attacks = Attacks.KNIGHT_ATTACKS[from] & ~friendly;
            addStandardMoves(from, attacks, enemy, moves);
            knights &= knights - 1;
        }
    }

    private static void generateKingMoves(Board board, boolean white, long friendly, long enemy, List<Move> moves) {
        long king = board.getBitboard(Piece.KING, white);
        if (king == 0) return; // Should not happen in standard chess, but guard against it
        int from = Bits.lsb(king);
        
        long attacks = Attacks.KING_ATTACKS[from] & ~friendly;
        addStandardMoves(from, attacks, enemy, moves);

        // Castling
        int castlingRights = board.getState().getCastlingRights();
        if (castlingRights != BoardState.NO_CASTLING && !isAttacked(board, from, !white)) {
            // Kingside
            int kRight = white ? BoardState.WHITE_KING_SIDE : BoardState.BLACK_KING_SIDE;
            if ((castlingRights & kRight) != 0) {
                long path = Castling.path(true, white);
                if ((board.getAllPieces() & path) == 0) {
                    // Check if squares king passes through are attacked
                    int passSq = from + 1;
                    int toSq = from + 2;
                    if (!isAttacked(board, passSq, !white) && !isAttacked(board, toSq, !white)) {
                        moves.add(Move.of(from, toSq, Move.FLAG_CASTLE_KING));
                    }
                }
            }
            // Queenside
            int qRight = white ? BoardState.WHITE_QUEEN_SIDE : BoardState.BLACK_QUEEN_SIDE;
            if ((castlingRights & qRight) != 0) {
                long path = Castling.path(false, white);
                if ((board.getAllPieces() & path) == 0) {
                    int passSq = from - 1;
                    int toSq = from - 2;
                    if (!isAttacked(board, passSq, !white) && !isAttacked(board, toSq, !white)) {
                        moves.add(Move.of(from, toSq, Move.FLAG_CASTLE_QUEEN));
                    }
                }
            }
        }
    }

    private static void generateSlidingMoves(Board board, boolean white, long friendly, long enemy, long allPieces, List<Move> moves) {
        // Bishops
        long bishops = board.getBitboard(Piece.BISHOP, white);
        while (bishops != 0) {
            int from = Bits.lsb(bishops);
            long attacks = Attacks.bishopAttacks(from, allPieces) & ~friendly;
            addStandardMoves(from, attacks, enemy, moves);
            bishops &= bishops - 1;
        }

        // Rooks
        long rooks = board.getBitboard(Piece.ROOK, white);
        while (rooks != 0) {
            int from = Bits.lsb(rooks);
            long attacks = Attacks.rookAttacks(from, allPieces) & ~friendly;
            addStandardMoves(from, attacks, enemy, moves);
            rooks &= rooks - 1;
        }

        // Queens
        long queens = board.getBitboard(Piece.QUEEN, white);
        while (queens != 0) {
            int from = Bits.lsb(queens);
            long attacks = Attacks.queenAttacks(from, allPieces) & ~friendly;
            addStandardMoves(from, attacks, enemy, moves);
            queens &= queens - 1;
        }
    }

    private static void addStandardMoves(int from, long attacks, long enemyPieces, List<Move> moves) {
        long captures = attacks & enemyPieces;
        long quiets = attacks & ~enemyPieces;

        while (captures != 0) {
            int to = Bits.lsb(captures);
            moves.add(Move.ofCapture(from, to));
            captures &= captures - 1;
        }

        while (quiets != 0) {
            int to = Bits.lsb(quiets);
            moves.add(Move.ofQuiet(from, to));
            quiets &= quiets - 1;
        }
    }
}

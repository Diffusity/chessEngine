package com.titan.movegen;

import com.titan.board.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Legal move generation using check-mask and pin-ray filtering.
 * <p>
 * For each position we compute once:
 * <ul>
 *   <li>checkers mask/count — enemy pieces giving check to our king</li>
 *   <li>push/capture masks — destinations that resolve a single check (block or capture
 *       the checker); unrestricted ({@code ALL}) when not in check</li>
 *   <li>{@code PIN_RAY_MASKS[sq]} — for each absolutely pinned piece, the ray between its
 *       king and its pinner (pinner inclusive); a pinned piece may only move along it</li>
 * </ul>
 * Destinations of every non-king move are then filtered with pure bit operations — no
 * make/unmake and no per-move attack scans. King moves are validated with direct attack
 * tests on an occupancy that excludes the king (so sliders x-ray through its square), and
 * en passant uses a dedicated simulation because it vacates two squares at once.
 * <p>
 * Working state lives in static fields: the engine is currently single-threaded (same
 * assumption as SEE's shared swap buffer).
 */
public class MoveGenerator {

    // Per-call working state
    private static int checkersCount;
    private static long captureMask;
    private static long pushMask;
    private static final long[] PIN_RAY_MASKS = new long[64];

    /** Squares strictly between two aligned squares (both endpoints excluded). */
    private static final long[][] BETWEEN = new long[64][64];

    private static final long ALL = -1L;

    static {
        initBetween();
    }

    private static void initBetween() {
        for (int sq1 = 0; sq1 < 64; sq1++) {
            for (int dir = 0; dir < 8; dir++) {
                int oppDir = (dir + 4) & 7;
                long rayFwd = Attacks.RAY_ATTACKS[dir][sq1];
                long bb = rayFwd;
                while (bb != 0) {
                    int sq2 = Bits.lsb(bb);
                    bb &= bb - 1;
                    BETWEEN[sq1][sq2] = rayFwd & Attacks.RAY_ATTACKS[oppDir][sq2];
                }
            }
        }
    }

    /**
     * Generates all strictly legal moves for the current position.
     *
     * @param board the board position
     * @return a list of all legal moves
     */
    public static List<Move> generateLegalMoves(Board board) {
        final boolean us = board.isWhiteToMove();
        final long friendly = board.getPieces(us);
        final long enemyPieces = board.getPieces(!us);
        final long occ = friendly | enemyPieces;

        final long kingBit = board.getBitboard(Piece.KING, us);
        if (kingBit == 0) {
            return generatePseudoLegalMoves(board); // degenerate position safeguard
        }
        final int kingSq = Bits.lsb(kingBit);

        computeCheckAndPinInfo(board, us, kingSq, friendly, enemyPieces, occ);

        List<Move> moves = new ArrayList<>(40);

        generateLegalKingMoves(board, us, kingSq, friendly, occ, moves, false);
        if (checkersCount >= 2) {
            return moves; // double check: only king moves are legal
        }

        generateLegalPawnMoves(board, us, enemyPieces, occ, moves, false);
        generateLegalKnightMoves(board, us, friendly, moves, false);
        generateLegalSlidingMoves(board, us, friendly, occ, moves, false);

        return moves;
    }

    /**
     * Generates all strictly legal capture moves for the current position:
     * captures, en-passant and promotion-captures. Quiet promotions are excluded,
     * preserving the behaviour expected by quiescence search.
     */
    public static List<Move> generateCaptures(Board board) {
        final boolean us = board.isWhiteToMove();
        final long friendly = board.getPieces(us);
        final long enemyPieces = board.getPieces(!us);
        final long occ = friendly | enemyPieces;

        final long kingBit = board.getBitboard(Piece.KING, us);
        if (kingBit == 0) return new ArrayList<>(0);
        final int kingSq = Bits.lsb(kingBit);

        computeCheckAndPinInfo(board, us, kingSq, friendly, enemyPieces, occ);

        List<Move> moves = new ArrayList<>(16);

        generateLegalKingMoves(board, us, kingSq, friendly, occ, moves, true);
        if (checkersCount >= 2) {
            return moves;
        }

        generateLegalPawnMoves(board, us, enemyPieces, occ, moves, true);
        generateLegalKnightMoves(board, us, friendly, moves, true);
        generateLegalSlidingMoves(board, us, friendly, occ, moves, true);

        return moves;
    }

    /**
     * Computes checkers, push/capture evasion masks and pin rays for the side to move.
     */
    private static void computeCheckAndPinInfo(Board board, boolean us, int kingSq,
                                               long friendly, long enemyPieces, long occ) {
        final long theirPawns = board.getBitboard(Piece.PAWN, !us);
        final long theirKnights = board.getBitboard(Piece.KNIGHT, !us);
        final long theirBishops = board.getBitboard(Piece.BISHOP, !us);
        final long theirRooks = board.getBitboard(Piece.ROOK, !us);
        final long theirQueens = board.getBitboard(Piece.QUEEN, !us);
        final long theirBQ = theirBishops | theirQueens;
        final long theirRQ = theirRooks | theirQueens;

        java.util.Arrays.fill(PIN_RAY_MASKS, 0L);

        long checkers = 0;
        // Enemy (=!us) pawn attackers of kingSq sit on the OPPOSITE pattern:
        // white enemy attackers stand below (black pattern), black above (white pattern).
        checkers |= Attacks.PAWN_ATTACKS[us ? 0 : 1][kingSq] & theirPawns;
        checkers |= Attacks.KNIGHT_ATTACKS[kingSq] & theirKnights;
        checkers |= Attacks.bishopAttacks(kingSq, occ) & theirBQ;
        checkers |= Attacks.rookAttacks(kingSq, occ) & theirRQ;

        checkersCount = Long.bitCount(checkers);

        if (checkersCount == 1) {
            captureMask = checkers;
            int checkerSq = Bits.lsb(checkers);
            long checkerBit = Bits.toBitboard(checkerSq);
            boolean sliderChecker = (checkerBit & (theirBQ | theirRQ)) != 0;
            pushMask = sliderChecker ? BETWEEN[checkerSq][kingSq] : 0L;
        } else if (checkersCount == 0) {
            captureMask = ALL;
            pushMask = ALL;
        } else {
            captureMask = 0L;
            pushMask = 0L;
        }

        if (checkersCount >= 2) return; // pins irrelevant in double check

        // Absolutely pinned pieces: x-ray from the king through OUR pieces only.
        // Enemy pieces remain in occupancy so they correctly block potential pins.
        final long occNoOurs = occ & ~friendly;
        long rookPinners = Attacks.rookAttacks(kingSq, occNoOurs) & theirRQ;
        long bishopPinners = Attacks.bishopAttacks(kingSq, occNoOurs) & theirBQ;

        while (rookPinners != 0) {
            int pinner = Bits.lsb(rookPinners);
            rookPinners &= rookPinners - 1;
            markPinnedPiece(pinner, kingSq, friendly);
        }
        while (bishopPinners != 0) {
            int pinner = Bits.lsb(bishopPinners);
            bishopPinners &= bishopPinners - 1;
            markPinnedPiece(pinner, kingSq, friendly);
        }
    }

    private static void markPinnedPiece(int pinnerSq, int kingSq, long friendly) {
        long between = BETWEEN[pinnerSq][kingSq];
        long pinned = between & friendly;
        if (Long.bitCount(pinned) == 1) {
            PIN_RAY_MASKS[Bits.lsb(pinned)] = between | Bits.toBitboard(pinnerSq);
        }
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
        return isAttacked(board, sq, byWhite, board.getAllPieces());
    }

    /**
     * Determines if a square is attacked using an explicit occupancy. This allows callers
     * to test squares with pieces conceptually removed (e.g., the king itself when
     * generating king moves, so sliders x-ray through its current square).
     *
     * @param board     the board position
     * @param sq        the target square (0-63)
     * @param byWhite   true to check if attacked by White, false for Black
     * @param occupancy occupancy bitboard to use for slider ray blocking
     * @return true if the square is attacked
     */
    public static boolean isAttacked(Board board, int sq, boolean byWhite, long occupancy) {
        long occupied = occupancy;

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
        int epSq = board.getState().getEnPassantSquare(); // hoisted out of pawn loop
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

    // ========================================================================
    // Legal (mask-filtered) generation — used by generateLegalMoves/generateCaptures
    // ========================================================================

    private static void generateLegalPawnMoves(Board board, boolean white, long enemyPieces,
                                               long occ, List<Move> moves, boolean capturesOnly) {
        long pawns = board.getBitboard(Piece.PAWN, white);
        if (pawns == 0) return;

        int pushOffset = white ? 8 : -8;
        long promoRankMask = white ? Bits.Rank.WHITE_PROMOTION : Bits.Rank.BLACK_PROMOTION;
        long emptySquares = ~occ;

        if (!capturesOnly) {
            long singlePushes = white ? (pawns << 8) & emptySquares : (pawns >>> 8) & emptySquares;
            long singlePushesPromo = singlePushes & promoRankMask;
            long singlePushesNormal = singlePushes & ~promoRankMask;

            // Derive doubles from the UNFILTERED single set: the first step only needs
            // to be empty, not evasion-relevant (e.g. b7b6 blocked-out would wrongly
            // kill the block b7b5).
            long doublePushes = white ? (singlePushesNormal << 8) & emptySquares
                                      : (singlePushesNormal >>> 8) & emptySquares;
            doublePushes &= white ? Bits.Rank._4 : Bits.Rank._5;

            long evasionMask = captureMask | pushMask;
            singlePushesNormal &= evasionMask;
            singlePushesPromo &= evasionMask;
            doublePushes &= pushMask;

            addPawnMovesFiltered(singlePushesNormal, -pushOffset, Move.FLAG_QUIET, moves);
            addPawnMovesFiltered(doublePushes, -(pushOffset * 2), Move.FLAG_PAWN_DOUBLE, moves);
            addPawnPromotionsFiltered(singlePushesPromo, -pushOffset, false, moves);
        }

        // Captures, promotion-captures and en passant
        int epSq = board.getState().getEnPassantSquare();
        long pawnsLeft = pawns;
        while (pawnsLeft != 0) {
            int from = Bits.lsb(pawnsLeft);
            pawnsLeft &= pawnsLeft - 1;

            long attacks = Attacks.PAWN_ATTACKS[white ? 0 : 1][from];
            long pinRay = PIN_RAY_MASKS[from];
            if (pinRay != 0) attacks &= pinRay;

            long captures = attacks & enemyPieces & captureMask;
            while (captures != 0) {
                int to = Bits.lsb(captures);
                captures &= captures - 1;
                if ((Bits.toBitboard(to) & promoRankMask) != 0) {
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_QUEEN_CAP));
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_ROOK_CAP));
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_BISHOP_CAP));
                    moves.add(Move.of(from, to, Move.FLAG_PROMO_KNIGHT_CAP));
                } else {
                    moves.add(Move.ofCapture(from, to));
                }
            }

            if (epSq != Bits.Square.NONE && (attacks & Bits.toBitboard(epSq)) != 0
                    && isEnPassantLegal(board, white, from, epSq)) {
                moves.add(Move.of(from, epSq, Move.FLAG_EP_CAPTURE));
            }
        }
    }

    private static void generateLegalKnightMoves(Board board, boolean white, long friendly,
                                                 List<Move> moves, boolean capturesOnly) {
        long knights = board.getBitboard(Piece.KNIGHT, white);
        if (knights == 0) return;
        long enemyPieces = board.getPieces(!white);
        long evasionMask = captureMask | pushMask;

        while (knights != 0) {
            int from = Bits.lsb(knights);
            knights &= knights - 1;
            if (PIN_RAY_MASKS[from] != 0) continue; // a pinned knight can never move

            long attacks = Attacks.KNIGHT_ATTACKS[from] & ~friendly & evasionMask;
            if (capturesOnly) attacks &= enemyPieces;
            addStandardMoves(from, attacks, enemyPieces, moves);
        }
    }

    private static void generateLegalKingMoves(Board board, boolean white, int kingSq,
                                               long friendly, long occ, List<Move> moves,
                                               boolean capturesOnly) {
        long enemyPieces = board.getPieces(!white);
        long attacks = Attacks.KING_ATTACKS[kingSq] & ~friendly;
        if (capturesOnly) attacks &= enemyPieces;

        long kingBit = Bits.toBitboard(kingSq);
        long occNoKing = occ ^ kingBit;

        long bb = attacks;
        while (bb != 0) {
            int to = Bits.lsb(bb);
            bb &= bb - 1;
            // Occupancy after the king lands there, capturing any occupant.
            long occAfter = occNoKing & ~Bits.toBitboard(to);
            if (!isAttacked(board, to, !white, occAfter)) {
                moves.add((Bits.toBitboard(to) & enemyPieces) != 0
                        ? Move.ofCapture(kingSq, to)
                        : Move.ofQuiet(kingSq, to));
            }
        }

        if (capturesOnly || checkersCount > 0) return;

        // Castling: rights + empty path + safe transit. Attack tests use occupancy
        // WITHOUT the king so that sliders behind it on the back rank are detected.
        int castlingRights = board.getState().getCastlingRights();
        if (castlingRights == BoardState.NO_CASTLING) return;

        boolean kingside = white
                ? (castlingRights & BoardState.WHITE_KING_SIDE) != 0
                : (castlingRights & BoardState.BLACK_KING_SIDE) != 0;
        boolean queenside = white
                ? (castlingRights & BoardState.WHITE_QUEEN_SIDE) != 0
                : (castlingRights & BoardState.BLACK_QUEEN_SIDE) != 0;

        if (kingside && (occ & Castling.path(true, white)) == 0) {
            int passSq = kingSq + 1;
            int toSq = kingSq + 2;
            if (!isAttacked(board, passSq, !white, occNoKing)
                    && !isAttacked(board, toSq, !white, occNoKing)) {
                moves.add(Move.of(kingSq, toSq, Move.FLAG_CASTLE_KING));
            }
        }
        if (queenside && (occ & Castling.path(false, white)) == 0) {
            int passSq = kingSq - 1;
            int toSq = kingSq - 2;
            if (!isAttacked(board, passSq, !white, occNoKing)
                    && !isAttacked(board, toSq, !white, occNoKing)) {
                moves.add(Move.of(kingSq, toSq, Move.FLAG_CASTLE_QUEEN));
            }
        }
    }

    private static void generateLegalSlidingMoves(Board board, boolean white, long friendly,
                                                  long occ, List<Move> moves, boolean capturesOnly) {
        long enemyPieces = board.getPieces(!white);
        long evasionMask = captureMask | pushMask;

        long bishops = board.getBitboard(Piece.BISHOP, white);
        while (bishops != 0) {
            int from = Bits.lsb(bishops);
            bishops &= bishops - 1;
            long attacks = Attacks.bishopAttacks(from, occ) & ~friendly & evasionMask;
            long pinRay = PIN_RAY_MASKS[from];
            if (pinRay != 0) attacks &= pinRay;
            if (capturesOnly) attacks &= enemyPieces;
            addStandardMoves(from, attacks, enemyPieces, moves);
        }

        long rooks = board.getBitboard(Piece.ROOK, white);
        while (rooks != 0) {
            int from = Bits.lsb(rooks);
            rooks &= rooks - 1;
            long attacks = Attacks.rookAttacks(from, occ) & ~friendly & evasionMask;
            long pinRay = PIN_RAY_MASKS[from];
            if (pinRay != 0) attacks &= pinRay;
            if (capturesOnly) attacks &= enemyPieces;
            addStandardMoves(from, attacks, enemyPieces, moves);
        }

        long queens = board.getBitboard(Piece.QUEEN, white);
        while (queens != 0) {
            int from = Bits.lsb(queens);
            queens &= queens - 1;
            long attacks = Attacks.queenAttacks(from, occ) & ~friendly & evasionMask;
            long pinRay = PIN_RAY_MASKS[from];
            if (pinRay != 0) attacks &= pinRay;
            if (capturesOnly) attacks &= enemyPieces;
            addStandardMoves(from, attacks, enemyPieces, moves);
        }
    }

    private static void addPawnMovesFiltered(long bitboard, int fromOffset, int flag, List<Move> moves) {
        while (bitboard != 0) {
            int to = Bits.lsb(bitboard);
            bitboard &= bitboard - 1;
            int from = to + fromOffset;
            long pinRay = PIN_RAY_MASKS[from];
            if (pinRay == 0 || (Bits.toBitboard(to) & pinRay) != 0) {
                moves.add(Move.of(from, to, flag));
            }
        }
    }

    private static void addPawnPromotionsFiltered(long bitboard, int fromOffset, boolean isCapture,
                                                  List<Move> moves) {
        while (bitboard != 0) {
            int to = Bits.lsb(bitboard);
            bitboard &= bitboard - 1;
            int from = to + fromOffset;
            long pinRay = PIN_RAY_MASKS[from];
            if (pinRay != 0 && (Bits.toBitboard(to) & pinRay) == 0) continue;
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
        }
    }

    /**
     * Full legality test for an en-passant capture. EP removes two pawns from one rank
     * structure in a single move, so masks alone are insufficient; we simulate the resulting
     * occupancy and test the king directly. This path is rare enough that the extra work is
     * negligible while remaining exactly correct (including horizontal discovered checks).
     */
    private static boolean isEnPassantLegal(Board board, boolean usWhite, int from, int to) {
        int capturedSq = to + (usWhite ? -8 : 8);
        long capturedBit = Bits.toBitboard(capturedSq);

        long occAfter = (board.getAllPieces() ^ Bits.toBitboard(from) ^ capturedBit)
                | Bits.toBitboard(to);

        long ourKing = board.getBitboard(Piece.KING, usWhite);
        if (ourKing == 0) return true;
        int kingSq = Bits.lsb(ourKing);

        final boolean them = !usWhite;
        long theirBQ = board.getBitboard(Piece.BISHOP, them) | board.getBitboard(Piece.QUEEN, them);
        long theirRQ = board.getBitboard(Piece.ROOK, them) | board.getBitboard(Piece.QUEEN, them);

        if ((Attacks.bishopAttacks(kingSq, occAfter) & theirBQ) != 0) return false;
        if ((Attacks.rookAttacks(kingSq, occAfter) & theirRQ) != 0) return false;
        if ((Attacks.KNIGHT_ATTACKS[kingSq] & board.getBitboard(Piece.KNIGHT, them)) != 0) return false;

        long theirPawns = board.getBitboard(Piece.PAWN, them) & ~capturedBit;
        if ((Attacks.PAWN_ATTACKS[usWhite ? 0 : 1][kingSq] & theirPawns) != 0) return false;

        if ((Attacks.KING_ATTACKS[kingSq] & board.getBitboard(Piece.KING, them)) != 0) return false;

        return true;
    }
}

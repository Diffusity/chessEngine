package com.titan.evaluation;

import com.titan.board.Board;
import com.titan.board.Piece;
import com.titan.board.Bits;

/**
 * Static evaluation function for the Titan Chess Engine.
 * <p>
 * Computes a heuristic score for a given board position using a tapered evaluation.
 * Tapered evaluation smoothly blends a middlegame score and an endgame score
 * based on the amount of material remaining on the board.
 * <p>
 * The score is returned from the perspective of the side to move (positive means
 * the side to move is winning).
 */
public class Evaluator {

    public static final int BISHOP_PAIR_BONUS = 30;
    
    // Static pawn hash table (e.g., 16384 entries)
    private static final com.titan.tables.PawnHashTable pawnHashTable = new com.titan.tables.PawnHashTable(16384);

    /**
     * Evaluates the given board position.
     *
     * @param board the current board state
     * @return the evaluation score in centipawns (from the side-to-move's perspective)
     */
    public static int evaluate(Board board) {
        int mgScore = 0;
        int egScore = 0;

        // 1. Piece-Square Tables (PST) and Material
        for (Piece p : Piece.values()) {
            // White pieces
            long whitePieces = board.getBitboard(p, true);
            while (whitePieces != 0) {
                int sq = Bits.lsb(whitePieces);
                mgScore += PieceSquareTables.mgValue(p, true, sq);
                egScore += PieceSquareTables.egValue(p, true, sq);
                whitePieces &= whitePieces - 1;
            }

            // Black pieces
            long blackPieces = board.getBitboard(p, false);
            while (blackPieces != 0) {
                int sq = Bits.lsb(blackPieces);
                mgScore -= PieceSquareTables.mgValue(p, false, sq);
                egScore -= PieceSquareTables.egValue(p, false, sq);
                blackPieces &= blackPieces - 1;
            }
        }

        // 2. Bishop Pair Bonus
        if (Long.bitCount(board.getBitboard(Piece.BISHOP, true)) >= 2) {
            mgScore += BISHOP_PAIR_BONUS;
            egScore += BISHOP_PAIR_BONUS;
        }
        if (Long.bitCount(board.getBitboard(Piece.BISHOP, false)) >= 2) {
            mgScore -= BISHOP_PAIR_BONUS;
            egScore -= BISHOP_PAIR_BONUS;
        }

        // 3. Advanced Pawn Structure Evaluation
        long pawnKey = board.pawnKey();
        com.titan.tables.PawnHashTable.PawnEntry pawnEntry = pawnHashTable.probe(pawnKey);
        
        long whitePawns = board.getBitboard(Piece.PAWN, true);
        long blackPawns = board.getBitboard(Piece.PAWN, false);

        int pawnMg = 0;
        int pawnEg = 0;
        
        if (pawnEntry != null) {
            pawnMg = pawnEntry.mgScore;
            pawnEg = pawnEntry.egScore;
        } else {
            int wDoubled = 0;
            int bDoubled = 0;
            int wIsolated = 0;
            int bIsolated = 0;
            int wPassedMG = 0;
            int wPassedEG = 0;
            int bPassedMG = 0;
            int bPassedEG = 0;
    
            // Passed pawn bonuses based on rank (from their side's perspective)
            // Ranks 1 to 8 (indices 0 to 7)
            int[] passedBonusMG = {0, 5, 10, 20, 35, 60, 100, 0};
            int[] passedBonusEG = {0, 10, 20, 40, 70, 120, 200, 0};
    
            // Evaluate White Pawns
            long wp = whitePawns;
            while (wp != 0) {
                int sq = Bits.lsb(wp);
                int file = sq & 7;
                int rank = sq >> 3;
    
                // Doubled
                if ((EvalBitboards.FRONT_SPAN_MASKS[1][sq] & whitePawns) != 0) {
                    wDoubled++;
                }
                // Isolated
                if ((EvalBitboards.ADJACENT_FILE_MASKS[file] & whitePawns) == 0) {
                    wIsolated++;
                }
                // Passed
                if ((EvalBitboards.PASSED_PAWN_MASKS[1][sq] & blackPawns) == 0) {
                    wPassedMG += passedBonusMG[rank];
                    wPassedEG += passedBonusEG[rank];
                }
    
                wp &= wp - 1;
            }
    
            // Evaluate Black Pawns
            long bp = blackPawns;
            while (bp != 0) {
                int sq = Bits.lsb(bp);
                int file = sq & 7;
                int rank = sq >> 3;
    
                // Doubled
                if ((EvalBitboards.FRONT_SPAN_MASKS[0][sq] & blackPawns) != 0) {
                    bDoubled++;
                }
                // Isolated
                if ((EvalBitboards.ADJACENT_FILE_MASKS[file] & blackPawns) == 0) {
                    bIsolated++;
                }
                // Passed
                if ((EvalBitboards.PASSED_PAWN_MASKS[0][sq] & whitePawns) == 0) {
                    int relativeRank = 7 - rank;
                    bPassedMG += passedBonusMG[relativeRank];
                    bPassedEG += passedBonusEG[relativeRank];
                }
    
                bp &= bp - 1;
            }
    
            pawnMg -= wDoubled * 15;
            pawnEg -= wDoubled * 20;
            pawnMg -= wIsolated * 15;
            pawnEg -= wIsolated * 20;
            pawnMg += wPassedMG;
            pawnEg += wPassedEG;
    
            pawnMg += bDoubled * 15;
            pawnEg += bDoubled * 20;
            pawnMg += bIsolated * 15;
            pawnEg += bIsolated * 20;
            pawnMg -= bPassedMG;
            pawnEg -= bPassedEG;
            
            pawnHashTable.store(pawnKey, pawnMg, pawnEg);
        }
        
        mgScore += pawnMg;
        egScore += pawnEg;

        // 4. King Safety (Pawn Shield) - only in middlegame
        int phase = GamePhase.calculate(board);
        if (phase > 12) {
            int wKingSq = Bits.lsb(board.getBitboard(Piece.KING, true));
            int bKingSq = Bits.lsb(board.getBitboard(Piece.KING, false));
            
            // White King Shield
            if (wKingSq != 64) {
                int file = wKingSq & 7;
                int rank = wKingSq >> 3;
                if (rank < 7) {
                    for (int f = Math.max(0, file - 1); f <= Math.min(7, file + 1); f++) {
                        long shieldSq = Bits.toBitboard(Bits.toSquare(f, rank + 1));
                        if ((shieldSq & whitePawns) == 0) {
                            mgScore -= 10;
                        }
                    }
                }
            }

            // Black King Shield
            if (bKingSq != 64) {
                int file = bKingSq & 7;
                int rank = bKingSq >> 3;
                if (rank > 0) {
                    for (int f = Math.max(0, file - 1); f <= Math.min(7, file + 1); f++) {
                        long shieldSq = Bits.toBitboard(Bits.toSquare(f, rank - 1));
                        if ((shieldSq & blackPawns) == 0) {
                            mgScore += 10;
                        }
                    }
                }
            }
        }

        // 5. Piece Mobility and Rook Bonuses
        long occupied = board.getAllPieces();
        long whiteOccupied = board.getWhitePieces();
        long blackOccupied = board.getBlackPieces();
        
        // Knights
        long wn = board.getBitboard(Piece.KNIGHT, true);
        while (wn != 0) {
            int sq = Bits.lsb(wn);
            long attacks = com.titan.movegen.Attacks.KNIGHT_ATTACKS[sq] & ~whiteOccupied;
            int mob = Long.bitCount(attacks);
            mgScore += mob * 2;
            egScore += mob * 2;
            wn &= wn - 1;
        }
        long bn = board.getBitboard(Piece.KNIGHT, false);
        while (bn != 0) {
            int sq = Bits.lsb(bn);
            long attacks = com.titan.movegen.Attacks.KNIGHT_ATTACKS[sq] & ~blackOccupied;
            int mob = Long.bitCount(attacks);
            mgScore -= mob * 2;
            egScore -= mob * 2;
            bn &= bn - 1;
        }

        // Bishops
        long wb = board.getBitboard(Piece.BISHOP, true);
        while (wb != 0) {
            int sq = Bits.lsb(wb);
            long attacks = com.titan.movegen.Attacks.bishopAttacks(sq, occupied) & ~whiteOccupied;
            int mob = Long.bitCount(attacks);
            mgScore += mob * 3;
            egScore += mob * 3;
            wb &= wb - 1;
        }
        long bb = board.getBitboard(Piece.BISHOP, false);
        while (bb != 0) {
            int sq = Bits.lsb(bb);
            long attacks = com.titan.movegen.Attacks.bishopAttacks(sq, occupied) & ~blackOccupied;
            int mob = Long.bitCount(attacks);
            mgScore -= mob * 3;
            egScore -= mob * 3;
            bb &= bb - 1;
        }

        // Rooks
        long wr = board.getBitboard(Piece.ROOK, true);
        while (wr != 0) {
            int sq = Bits.lsb(wr);
            long attacks = com.titan.movegen.Attacks.rookAttacks(sq, occupied) & ~whiteOccupied;
            int mob = Long.bitCount(attacks);
            mgScore += mob * 2;
            egScore += mob * 2;

            int file = sq & 7;
            int rank = sq >> 3;
            
            // Open/Semi-open file
            if ((whitePawns & Bits.File.MASKS[file]) == 0) {
                if ((blackPawns & Bits.File.MASKS[file]) == 0) {
                    mgScore += 20; egScore += 10; // Open file
                } else {
                    mgScore += 10; egScore += 5; // Semi-open file
                }
            }
            
            // 7th rank
            if (rank == 6) {
                mgScore += 20; egScore += 30;
            }

            wr &= wr - 1;
        }
        long br = board.getBitboard(Piece.ROOK, false);
        while (br != 0) {
            int sq = Bits.lsb(br);
            long attacks = com.titan.movegen.Attacks.rookAttacks(sq, occupied) & ~blackOccupied;
            int mob = Long.bitCount(attacks);
            mgScore -= mob * 2;
            egScore -= mob * 2;

            int file = sq & 7;
            int rank = sq >> 3;
            
            // Open/Semi-open file
            if ((blackPawns & Bits.File.MASKS[file]) == 0) {
                if ((whitePawns & Bits.File.MASKS[file]) == 0) {
                    mgScore -= 20; egScore -= 10; // Open file
                } else {
                    mgScore -= 10; egScore -= 5; // Semi-open file
                }
            }
            
            // 7th rank
            if (rank == 1) {
                mgScore -= 20; egScore -= 30;
            }

            br &= br - 1;
        }

        // Queens
        long wq = board.getBitboard(Piece.QUEEN, true);
        while (wq != 0) {
            int sq = Bits.lsb(wq);
            long attacks = com.titan.movegen.Attacks.queenAttacks(sq, occupied) & ~whiteOccupied;
            int mob = Long.bitCount(attacks);
            mgScore += mob;
            egScore += mob;
            wq &= wq - 1;
        }
        long bq = board.getBitboard(Piece.QUEEN, false);
        while (bq != 0) {
            int sq = Bits.lsb(bq);
            long attacks = com.titan.movegen.Attacks.queenAttacks(sq, occupied) & ~blackOccupied;
            int mob = Long.bitCount(attacks);
            mgScore -= mob;
            egScore -= mob;
            bq &= bq - 1;
        }

        // 6. Tapered Phase Blending
        int score = (mgScore * phase + egScore * (GamePhase.MAX_PHASE - phase)) / GamePhase.MAX_PHASE;

        // 7. Return relative to the side to move (Negamax framework requires this)
        return board.isWhiteToMove() ? score : -score;
    }
}

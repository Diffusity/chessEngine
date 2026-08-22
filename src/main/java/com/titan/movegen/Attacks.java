package com.titan.movegen;

import com.titan.board.Bits;
import com.titan.board.Color;

public class Attacks {

    public static final long[] KING_ATTACKS = new long[64];
    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[][] PAWN_ATTACKS = new long[2][64]; // [Color][Square]
    public static final long[][] RAY_ATTACKS = new long[8][64];  // [Direction][Square]
    public static final long[][] RAY_BETWEEN = new long[64][64]; // [Square1][Square2]

    // ========================
    // Magic Bitboards
    // Published magic constants (same little-endian a1=0 mapping as Titan).
    // ========================

    private static final long[] ROOK_MAGICS = {
            0x0080001020400080L, 0x0040001000200040L, 0x0080081000200080L, 0x0080040800100080L,
            0x0080020400080080L, 0x0080010200040080L, 0x0080008001000200L, 0x0080002040800100L,
            0x0000800020400080L, 0x0000400020005000L, 0x0000801000200080L, 0x0000800800100080L,
            0x0000800400080080L, 0x0000800200040080L, 0x0000800100020080L, 0x0000800040800100L,
            0x0000208000400080L, 0x0000404000201000L, 0x0000808010002000L, 0x0000808008001000L,
            0x0000808004000800L, 0x0000808002000400L, 0x0000010100020004L, 0x0000020000408104L,
            0x0000208080004000L, 0x0000200040005000L, 0x0000100080200080L, 0x0000080080100080L,
            0x0000040080080080L, 0x0000020080040080L, 0x0000010080800200L, 0x0000800080004100L,
            0x0000204000800080L, 0x0000200040401000L, 0x0000100080802000L, 0x0000080080801000L,
            0x0000040080800800L, 0x0000020080800400L, 0x0000020001010004L, 0x0000800040800100L,
            0x0000204000808000L, 0x0000200040008080L, 0x0000100020008080L, 0x0000080010008080L,
            0x0000040008008080L, 0x0000020004008080L, 0x0000010002008080L, 0x0000004081020004L,
            0x0000204000800080L, 0x0000200040008080L, 0x0000100020008080L, 0x0000080010008080L,
            0x0000040008008080L, 0x0000020004008080L, 0x0000800100020080L, 0x0000800041000080L,
            0x00FFFCDDFCED714AL, 0x007FFCDDFCED714AL, 0x003FFFCDFFD88096L, 0x0000040810002101L,
            0x0001000204080011L, 0x0001000204000801L, 0x0001000082000401L, 0x0001FFFAABFAD1A2L
    };

    private static final long[] BISHOP_MAGICS = {
            0x0002020202020200L, 0x0002020202020000L, 0x0004010202000000L, 0x0004040080000000L,
            0x0001104000000000L, 0x0000821040000000L, 0x0000410410400000L, 0x0000104104104000L,
            0x0000040404040400L, 0x0000020202020200L, 0x0000040102020000L, 0x0000040400800000L,
            0x0000011040000000L, 0x0000008210400000L, 0x0000004104104000L, 0x0000002082082000L,
            0x0004000808080800L, 0x0002000404040400L, 0x0001000202020200L, 0x0000800802004000L,
            0x0000800400A00000L, 0x0000200100884000L, 0x0000400082082000L, 0x0000200041041000L,
            0x0002080010101000L, 0x0001040008080800L, 0x0000208004010400L, 0x0000404004010200L,
            0x0000840000802000L, 0x0000404002011000L, 0x0000808001041000L, 0x0000404000820800L,
            0x0001041000202000L, 0x0000820800101000L, 0x0000104400080800L, 0x0000020080080080L,
            0x0000404040040100L, 0x0000808100020100L, 0x0001010100020800L, 0x0000808080010400L,
            0x0000820820004000L, 0x0000410410002000L, 0x0000082088001000L, 0x0000002011000800L,
            0x0000080100400400L, 0x0001010101000200L, 0x0002020202000400L, 0x0001010101000200L,
            0x0000410410400000L, 0x0000208208200000L, 0x0000002084100000L, 0x0000000020880000L,
            0x0000001002020000L, 0x0000040408020000L, 0x0004040404040000L, 0x0002020202020000L,
            0x0000104104104000L, 0x0000002082082000L, 0x0000000020841000L, 0x0000000000208800L,
            0x0000000010020200L, 0x0000000404080200L, 0x0000040404040400L, 0x0002020202020200L
    };

    private static final int[] ROOK_SHIFTS = {
            52, 53, 53, 53, 53, 53, 53, 52,
            53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 53, 53, 53, 53, 53
    };

    private static final int[] BISHOP_SHIFTS = {
            58, 59, 59, 59, 59, 59, 59, 58,
            59, 59, 59, 59, 59, 59, 59, 59,
            59, 59, 57, 57, 57, 57, 59, 59,
            59, 59, 57, 55, 55, 57, 59, 59,
            59, 59, 57, 55, 55, 57, 59, 59,
            59, 59, 57, 57, 57, 57, 59, 59,
            59, 59, 59, 59, 59, 59, 59, 59,
            58, 59, 59, 59, 59, 59, 59, 58
    };

    /** Per-square magic attack databases. */
    private static final long[][] ROOK_MAGIC_ATTACKS = new long[64][];
    private static final long[][] BISHOP_MAGIC_ATTACKS = new long[64][];
    private static final long[] ROOK_MASKS = new long[64];
    private static final long[] BISHOP_MASKS = new long[64];

    // Directions for RAY_ATTACKS: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW
    public static final int DIR_N = 0;
    public static final int DIR_NE = 1;
    public static final int DIR_E = 2;
    public static final int DIR_SE = 3;
    public static final int DIR_S = 4;
    public static final int DIR_SW = 5;
    public static final int DIR_W = 6;
    public static final int DIR_NW = 7;

    private static final int[] DF = { 0,  1,  1,  1,  0, -1, -1, -1};
    private static final int[] DR = { 1,  1,  0, -1, -1, -1,  0,  1};

    private static final int[] ORTHO_DIRS = { DIR_N, DIR_E, DIR_S, DIR_W };
    private static final int[] DIAG_DIRS = { DIR_NE, DIR_SE, DIR_SW, DIR_NW };

    static {
        initKingAttacks();
        initKnightAttacks();
        initPawnAttacks();
        initRays();
        initRayBetween();
        initMagicTables();
    }

    // ========================
    // Public attack lookups
    // ========================

    private static void initKingAttacks() {
        for (int sq = 0; sq < 64; sq++) {
            long attacks = 0;
            int r = Bits.rankOf(sq);
            int f = Bits.fileOf(sq);
            for (int i = 0; i < 8; i++) {
                int nr = r + DR[i];
                int nf = f + DF[i];
                if (nr >= 0 && nr <= 7 && nf >= 0 && nf <= 7) {
                    attacks |= Bits.toBitboard(Bits.toSquare(nf, nr));
                }
            }
            KING_ATTACKS[sq] = attacks;
        }
    }

    private static void initKnightAttacks() {
        int[] ndf = {1, 2, 2, 1, -1, -2, -2, -1};
        int[] ndr = {2, 1, -1, -2, -2, -1, 1, 2};
        for (int sq = 0; sq < 64; sq++) {
            long attacks = 0;
            int r = Bits.rankOf(sq);
            int f = Bits.fileOf(sq);
            for (int i = 0; i < 8; i++) {
                int nr = r + ndr[i];
                int nf = f + ndf[i];
                if (nr >= 0 && nr <= 7 && nf >= 0 && nf <= 7) {
                    attacks |= Bits.toBitboard(Bits.toSquare(nf, nr));
                }
            }
            KNIGHT_ATTACKS[sq] = attacks;
        }
    }

    private static void initPawnAttacks() {
        for (int sq = 0; sq < 64; sq++) {
            int r = Bits.rankOf(sq);
            int f = Bits.fileOf(sq);

            // White pawn attacks
            long wAttacks = 0;
            if (r < 7) {
                if (f > 0) wAttacks |= Bits.toBitboard(Bits.toSquare(f - 1, r + 1));
                if (f < 7) wAttacks |= Bits.toBitboard(Bits.toSquare(f + 1, r + 1));
            }
            PAWN_ATTACKS[Color.WHITE.index()][sq] = wAttacks;

            // Black pawn attacks
            long bAttacks = 0;
            if (r > 0) {
                if (f > 0) bAttacks |= Bits.toBitboard(Bits.toSquare(f - 1, r - 1));
                if (f < 7) bAttacks |= Bits.toBitboard(Bits.toSquare(f + 1, r - 1));
            }
            PAWN_ATTACKS[Color.BLACK.index()][sq] = bAttacks;
        }
    }

    private static void initRays() {
        for (int dir = 0; dir < 8; dir++) {
            for (int sq = 0; sq < 64; sq++) {
                long ray = 0;
                int r = Bits.rankOf(sq);
                int f = Bits.fileOf(sq);
                int nr = r + DR[dir];
                int nf = f + DF[dir];
                while (nr >= 0 && nr <= 7 && nf >= 0 && nf <= 7) {
                    ray |= Bits.toBitboard(Bits.toSquare(nf, nr));
                    nr += DR[dir];
                    nf += DF[dir];
                }
                RAY_ATTACKS[dir][sq] = ray;
            }
        }
    }

    private static void initRayBetween() {
        for (int sq1 = 0; sq1 < 64; sq1++) {
            for (int sq2 = 0; sq2 < 64; sq2++) {
                RAY_BETWEEN[sq1][sq2] = 0;
                if (sq1 == sq2) continue;

                for (int dir = 0; dir < 8; dir++) {
                    if ((RAY_ATTACKS[dir][sq1] & Bits.toBitboard(sq2)) != 0) {
                        // sq2 is on the ray from sq1 in direction `dir`
                        long ray1 = RAY_ATTACKS[dir][sq1];
                        long ray2 = RAY_ATTACKS[dir][sq2];
                        RAY_BETWEEN[sq1][sq2] = ray1 ^ ray2 ^ Bits.toBitboard(sq2);
                        break;
                    }
                }
            }
        }
    }

    public static long bishopAttacks(int sq, long occupied) {
        long blockers = occupied & BISHOP_MASKS[sq];
        return BISHOP_MAGIC_ATTACKS[sq][(int) ((blockers * BISHOP_MAGICS[sq]) >>> BISHOP_SHIFTS[sq])];
    }

    public static long rookAttacks(int sq, long occupied) {
        long blockers = occupied & ROOK_MASKS[sq];
        return ROOK_MAGIC_ATTACKS[sq][(int) ((blockers * ROOK_MAGICS[sq]) >>> ROOK_SHIFTS[sq])];
    }

    public static long queenAttacks(int sq, long occupied) {
        return bishopAttacks(sq, occupied) | rookAttacks(sq, occupied);
    }

    // ========================
    // Magic table initialization
    // ========================

    private static void initMagicTables() {
        for (int sq = 0; sq < 64; sq++) {
            ROOK_MASKS[sq] = movementMask(sq, ORTHO_DIRS);
            BISHOP_MASKS[sq] = movementMask(sq, DIAG_DIRS);
            ROOK_MAGIC_ATTACKS[sq] = buildMagicTable(sq, ORTHO_DIRS, ROOK_MASKS[sq], ROOK_MAGICS[sq], ROOK_SHIFTS[sq]);
            BISHOP_MAGIC_ATTACKS[sq] = buildMagicTable(sq, DIAG_DIRS, BISHOP_MASKS[sq], BISHOP_MAGICS[sq], BISHOP_SHIFTS[sq]);
        }
    }

    /** Squares a slider could pass through, excluding the board-edge square on each ray. */
    private static long movementMask(int sq, int[] dirs) {
        int r0 = Bits.rankOf(sq);
        int f0 = Bits.fileOf(sq);
        long mask = 0;
        for (int d : dirs) {
            int r = r0 + DR[d];
            int f = f0 + DF[d];
            while (r >= 0 && r <= 7 && f >= 0 && f <= 7) {
                int nr = r + DR[d];
                int nf = f + DF[d];
                if (nr < 0 || nr > 7 || nf < 0 || nf > 7) break; // exclude edge square
                mask |= Bits.toBitboard(Bits.toSquare(f, r));
                r = nr;
                f = nf;
            }
        }
        return mask;
    }

    /** Attacks from sq given blocker set: walk each ray until (and including) first blocker. */
    private static long attackMask(int sq, long blockers, int[] dirs) {
        int r0 = Bits.rankOf(sq);
        int f0 = Bits.fileOf(sq);
        long attacks = 0;
        for (int d : dirs) {
            int r = r0 + DR[d];
            int f = f0 + DF[d];
            while (r >= 0 && r <= 7 && f >= 0 && f <= 7) {
                int s = Bits.toSquare(f, r);
                long bb = Bits.toBitboard(s);
                attacks |= bb;
                if ((blockers & bb) != 0) break;
                r += DR[d];
                f += DF[d];
            }
        }
        return attacks;
    }

    /** Builds the per-square magic table by enumerating all blocker subsets of the mask. */
    private static long[] buildMagicTable(int sq, int[] dirs, long mask, long magic, int shift) {
        int numBits = Long.bitCount(mask);
        long[] table = new long[1 << numBits];

        long subset = 0;
        do {
            long attacks = attackMask(sq, subset, dirs);
            table[(int) ((subset * magic) >>> shift)] = attacks;
            subset = (subset - mask) & mask; // carry-rippler next subset
        } while (subset != 0);
        return table;
    }
}

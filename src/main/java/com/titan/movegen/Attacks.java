package com.titan.movegen;

import com.titan.board.Bits;
import com.titan.board.Color;

public class Attacks {

    public static final long[] KING_ATTACKS = new long[64];
    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[][] PAWN_ATTACKS = new long[2][64]; // [Color][Square]
    public static final long[][] RAY_ATTACKS = new long[8][64];  // [Direction][Square]
    public static final long[][] RAY_BETWEEN = new long[64][64]; // [Square1][Square2]

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

    static {
        initKingAttacks();
        initKnightAttacks();
        initPawnAttacks();
        initRays();
        initRayBetween();
    }

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
        long attacks = 0;
        int r = Bits.rankOf(sq);
        int f = Bits.fileOf(sq);
        
        // NE
        for (int i=1; r+i<=7 && f+i<=7; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f+i, r+i));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        // SE
        for (int i=1; r-i>=0 && f+i<=7; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f+i, r-i));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        // SW
        for (int i=1; r-i>=0 && f-i>=0; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f-i, r-i));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        // NW
        for (int i=1; r+i<=7 && f-i>=0; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f-i, r+i));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        return attacks;
    }

    public static long rookAttacks(int sq, long occupied) {
        long attacks = 0;
        int r = Bits.rankOf(sq);
        int f = Bits.fileOf(sq);
        
        // N
        for (int i=1; r+i<=7; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f, r+i));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        // S
        for (int i=1; r-i>=0; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f, r-i));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        // E
        for (int i=1; f+i<=7; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f+i, r));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        // W
        for (int i=1; f-i>=0; i++) {
            long bb = Bits.toBitboard(Bits.toSquare(f-i, r));
            attacks |= bb;
            if ((occupied & bb) != 0) break;
        }
        return attacks;
    }

    public static long queenAttacks(int sq, long occupied) {
        return bishopAttacks(sq, occupied) | rookAttacks(sq, occupied);
    }
}

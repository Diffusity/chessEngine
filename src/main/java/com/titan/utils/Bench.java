package com.titan.utils;

import com.titan.board.Board;
import com.titan.search.SearchLimits;
import com.titan.search.SearchResult;
import com.titan.search.Searcher;

public class Bench {

    private static final String[] FENS = {
            FEN.STARTING_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -", // Kiwipete
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -", // Position 3
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq -", // Position 4
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ -", // Position 5
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - -", // Position 6
            "1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - 0 1", // Tactics
            "3r1k2/4npp1/1ppr3p/p6P/P2PPPP1/1NR5/5K2/2R5 w - - 0 1" // Endgame
    };

    public static void run(int targetDepth) {
        System.out.println("Running benchmark...");
        System.out.println("Positions: " + FENS.length);
        System.out.println("Target Depth: " + targetDepth);
        System.out.println();

        long totalNodes = 0;
        long totalTime = 0;

        for (int i = 0; i < FENS.length; i++) {
            Board board = FEN.parse(FENS[i]);
            Searcher searcher = new Searcher(board);

            SearchLimits limits = new SearchLimits();
            limits.depth = targetDepth;

            long start = System.currentTimeMillis();
            SearchResult result = searcher.search(board, limits);
            long end = System.currentTimeMillis();

            long time = end - start;
            totalNodes += result.nodes();
            totalTime += time;

            System.out.printf("Position %d: nodes %d time %dms\n", (i + 1), result.nodes(), time);
        }

        System.out.println();
        System.out.println("===================================");
        long nps = (totalTime > 0) ? (totalNodes * 1000L / totalTime) : 0;
        System.out.printf("Total: %d nodes in %dms (%d nps)\n", totalNodes, totalTime, nps);
    }
}

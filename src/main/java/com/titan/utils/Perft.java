package com.titan.utils;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.movegen.MoveGenerator;

import java.util.List;

/**
 * Perft (Performance Test) utility for validating move generation correctness.
 * <p>
 * Perft recursively counts the number of leaf nodes (possible positions) at a given depth.
 * This is the definitive way to verify that a move generator is 100% bug-free — if the
 * node counts match published values, the generator is correct.
 * <p>
 * Additionally provides:
 * <ul>
 *   <li><b>Divide mode</b>: Break down node count by root move (for debugging)</li>
 *   <li><b>NPS reporting</b>: Nodes per second for performance benchmarking</li>
 * </ul>
 *
 * @see <a href="https://www.chessprogramming.org/Perft">Chess Programming Wiki — Perft</a>
 */
public final class Perft {

    private Perft() {
        // Utility class
    }

    /**
     * Counts the number of leaf nodes at the given depth from the current position.
     *
     * @param board the board position
     * @param depth the search depth (0 returns 1)
     * @return the number of leaf positions
     */
    public static long perft(Board board, int depth) {
        if (depth == 0) return 1L;

        List<Move> moves = MoveGenerator.generateLegalMoves(board);

        if (depth == 1) {
            return moves.size();
        }

        long nodes = 0;
        for (Move move : moves) {
            board.makeMove(move);
            nodes += perft(board, depth - 1);
            board.unmakeMove();
        }
        return nodes;
    }

    /**
     * Runs perft in "divide" mode — prints the node count for each root move.
     * This is invaluable for debugging: if the total count is wrong, divide mode
     * shows which specific move's subtree has the wrong count.
     *
     * @param board the board position
     * @param depth the search depth
     * @return the total number of leaf positions
     */
    public static long divide(Board board, int depth) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);

        long total = 0;
        for (Move move : moves) {
            board.makeMove(move);
            long count = perft(board, depth - 1);
            board.unmakeMove();

            System.out.printf("%s: %d%n", move.toUCI(), count);
            total += count;
        }

        System.out.printf("%nTotal: %d (depth %d, %d root moves)%n", total, depth, moves.size());
        return total;
    }

    /**
     * Runs perft with timing information and reports nodes per second (NPS).
     *
     * @param board the board position
     * @param depth the search depth
     * @return the total number of leaf positions
     */
    public static long perftTimed(Board board, int depth) {
        System.out.printf("Running perft(%d)...%n", depth);
        long startTime = System.nanoTime();

        long nodes = perft(board, depth);

        long elapsed = System.nanoTime() - startTime;
        double seconds = elapsed / 1_000_000_000.0;
        long nps = seconds > 0 ? (long) (nodes / seconds) : nodes;

        System.out.printf("Nodes: %,d%n", nodes);
        System.out.printf("Time:  %.3f s%n", seconds);
        System.out.printf("NPS:   %,d%n", nps);

        return nodes;
    }
}

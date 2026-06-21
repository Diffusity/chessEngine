package com.titan.search;

import com.titan.board.Move;
import java.util.List;

/**
 * Record holding the result of a completed search iteration.
 */
public record SearchResult(
    Move bestMove,
    int score,
    int depth,
    long nodes,
    long timeMs,
    List<Move> pv
) {
    /**
     * Calculates nodes per second (NPS).
     */
    public long nps() {
        if (timeMs == 0) return nodes * 1000; // rough estimate if time is 0
        return (nodes * 1000) / timeMs;
    }

    /**
     * Returns the result in the standard UCI `info` format.
     */
    public String toUCIString() {
        StringBuilder sb = new StringBuilder();
        sb.append("info depth ").append(depth);

        if (Score.isMateScore(score)) {
            int plies = Score.mateInPly(score);
            int moves = (plies + 1) / 2;
            if (score < 0) moves = -moves;
            sb.append(" score mate ").append(moves);
        } else {
            sb.append(" score cp ").append(score);
        }

        sb.append(" time ").append(timeMs)
          .append(" nodes ").append(nodes)
          .append(" nps ").append(nps());

        if (pv != null && !pv.isEmpty()) {
            sb.append(" pv");
            for (Move m : pv) {
                sb.append(" ").append(m.toUCI());
            }
        }

        return sb.toString();
    }
}

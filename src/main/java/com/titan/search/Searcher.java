package com.titan.search;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.evaluation.Evaluator;
import com.titan.movegen.MoveGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Core search implementation — iterative deepening + alpha-beta + quiescence.
 */
public class Searcher implements Search {

    private final Board board;
    private volatile boolean stopped;
    private long nodes;

    private Move[][] pvTable;
    private int[] pvLength;

    private SearchStack searchStack;
    private SearchLimits limits;

    public Searcher(Board board) {
        this.board = board;
        this.pvTable = new Move[MAX_DEPTH][MAX_DEPTH];
        this.pvLength = new int[MAX_DEPTH];
        this.searchStack = new SearchStack(MAX_DEPTH + 4);
    }

    @Override
    public SearchResult search(Board board, SearchLimits limits) {
        this.limits = limits;
        this.stopped = false;
        this.nodes = 0;
        this.searchStack.clear();

        for (int i = 0; i < MAX_DEPTH; i++) {
            Arrays.fill(pvTable[i], null);
            pvLength[i] = 0;
        }

        return iterativeDeepening();
    }

    @Override
    public void stop() {
        this.stopped = true;
    }

    private SearchResult iterativeDeepening() {
        int targetDepth = limits.depth > 0 ? limits.depth : MAX_DEPTH;
        Move bestMove = null;
        int bestScore = Score.NONE;
        int depthReached = 0;

        for (int depth = 1; depth <= targetDepth; depth++) {
            int score = alphaBeta(depth, 0, -Score.INFINITY, Score.INFINITY, true);

            if (stopped && depth > 1) {
                break;
            }

            bestScore = score;
            bestMove = pvTable[0][0];
            depthReached = depth;

            // Extract PV
            List<Move> pv = new ArrayList<>();
            for (int i = 0; i < pvLength[0]; i++) {
                pv.add(pvTable[0][i]);
            }

            SearchResult result = new SearchResult(
                    bestMove, bestScore, depthReached, nodes,
                    (System.nanoTime() - limits.startTime) / 1_000_000,
                    pv
            );
            System.out.println(result.toUCIString());

            if (Score.isMateScore(bestScore)) {
                break; // Don't search deeper if we found a forced mate
            }

            if (!limits.shouldDeepen()) {
                break;
            }
        }

        // Return the best result found
        List<Move> finalPv = new ArrayList<>();
        for (int i = 0; i < pvLength[0]; i++) {
            finalPv.add(pvTable[0][i]);
        }
        
        return new SearchResult(
                bestMove, bestScore, depthReached, nodes,
                (System.nanoTime() - limits.startTime) / 1_000_000,
                finalPv
        );
    }

    private int alphaBeta(int depth, int ply, int alpha, int beta, boolean allowNull) {
        // Time check every 2048 nodes
        if ((nodes & 2047) == 0 && limits.isTimeUp()) {
            stopped = true;
        }

        if (stopped) return 0;

        pvLength[ply] = ply;

        // Draw detection
        if (ply > 0 && isDraw()) {
            return Score.DRAW;
        }

        // Check extension
        boolean inCheck = MoveGenerator.isInCheck(board, board.isWhiteToMove());
        if (inCheck) {
            depth++;
        }

        if (depth <= 0) {
            return quiescence(alpha, beta, ply);
        }

        if (ply >= MAX_DEPTH - 1) {
            return Evaluator.evaluate(board);
        }

        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        if (moves.isEmpty()) {
            if (inCheck) {
                return -Score.mateScore(ply);
            } else {
                return Score.DRAW;
            }
        }

        int movesSearched = 0;

        for (Move move : moves) {
            board.makeMove(move);
            nodes++;
            searchStack.get(ply).currentMove = move;

            int score = -alphaBeta(depth - 1, ply + 1, -beta, -alpha, true);

            board.unmakeMove();

            if (stopped) return 0;

            if (score > alpha) {
                if (score >= beta) {
                    return beta; // beta cutoff
                }
                alpha = score;
                updatePV(ply, move);
            }
            movesSearched++;
        }

        return alpha;
    }

    private int quiescence(int alpha, int beta, int ply) {
        if ((nodes & 2047) == 0 && limits.isTimeUp()) {
            stopped = true;
        }

        if (stopped) return 0;

        if (ply >= MAX_DEPTH - 1) {
            return Evaluator.evaluate(board);
        }

        // Draw detection
        if (isDraw()) {
            return Score.DRAW;
        }

        int standPat = Evaluator.evaluate(board);
        if (standPat >= beta) {
            return beta;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        List<Move> captures = MoveGenerator.generateCaptures(board);

        for (Move move : captures) {
            board.makeMove(move);
            nodes++;
            int score = -quiescence(-beta, -alpha, ply + 1);
            board.unmakeMove();

            if (stopped) return 0;

            if (score > alpha) {
                if (score >= beta) {
                    return beta;
                }
                alpha = score;
            }
        }

        return alpha;
    }

    private boolean isDraw() {
        return board.isRepetition() || board.isFiftyMoveRule() || board.isInsufficientMaterial();
    }

    private void updatePV(int ply, Move move) {
        pvTable[ply][ply] = move;
        for (int i = ply + 1; i < pvLength[ply + 1]; i++) {
            pvTable[ply][i] = pvTable[ply + 1][i];
        }
        pvLength[ply] = pvLength[ply + 1];
    }
}

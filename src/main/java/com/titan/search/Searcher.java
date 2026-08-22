package com.titan.search;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.board.Piece;
import com.titan.evaluation.Evaluator;
import com.titan.movegen.MoveGenerator;
import com.titan.tables.history.CounterMoveTable;
import com.titan.tables.history.HistoryTable;
import com.titan.tables.history.KillerTable;
import com.titan.tables.tt.HashEntry;
import com.titan.tables.tt.HashFlag;
import com.titan.tables.tt.TranspositionTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Core search implementation — iterative deepening + alpha-beta + quiescence.
 */
public class Searcher implements Search {

    private Board board;
    private volatile boolean stopped;
    private long nodes;

    private Move[][] pvTable;
    private int[] pvLength;

    private SearchStack searchStack;
    private SearchLimits limits;
    
    private TranspositionTable tt;
    private KillerTable killerTable;
    private HistoryTable historyTable;
    private CounterMoveTable counterMoveTable;
    
    // Futility margins based on depth
    private static final int[] futilityMargin = { 0, 200, 300, 500 };

    /**
     * Precomputed Late Move Reduction table: LMR[depth][moveNumber]. Replaces per-move
     * {@code Math.log} calls in the hot path.
     */
    private static final int[][] LMR = new int[MAX_DEPTH][MAX_DEPTH];

    static {
        for (int d = 1; d < MAX_DEPTH; d++) {
            for (int m = 1; m < MAX_DEPTH; m++) {
                LMR[d][m] = (int) (0.75 + Math.log(d) * Math.log(m) / 2.25);
            }
        }
    }

    public Searcher(Board board) {
        this.board = board;
        this.pvTable = new Move[MAX_DEPTH][MAX_DEPTH];
        this.pvLength = new int[MAX_DEPTH];
        this.searchStack = new SearchStack(MAX_DEPTH + 4);
        
        // 64 MB default size
        this.tt = new TranspositionTable(64);
        this.killerTable = new KillerTable(MAX_DEPTH);
        this.historyTable = new HistoryTable();
        this.counterMoveTable = new CounterMoveTable();
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public TranspositionTable getTranspositionTable() {
        return tt;
    }

    @Override
    public SearchResult search(Board board, SearchLimits limits) {
        this.limits = limits;
        if (limits.startTime == 0) {
            limits.startTime = System.nanoTime();
        }
        this.stopped = false;
        this.nodes = 0;
        this.searchStack.clear();
        this.killerTable.clear();
        this.counterMoveTable.clear();

        this.tt.newSearch();
        this.historyTable.age();

        for (int i = 0; i < MAX_DEPTH; i++) {
            Arrays.fill(pvTable[i], null);
            pvLength[i] = 0;
        }

        List<Move> rootMoves = MoveGenerator.generateLegalMoves(board);
        if (rootMoves.isEmpty()) {
            return new SearchResult(null, Score.NONE, 0, 0,
                    (System.nanoTime() - limits.startTime) / 1_000_000, List.of());
        }
        // Fallback move: TT move if available, else the first legal move.
        // Guarantees we never emit "bestmove 0000" while legal moves exist.
        Move fallbackMove = rootMoves.get(0);
        HashEntry rootEntry = tt.probe(board.key(), 0);
        if (rootEntry != null && rootEntry.move != 0) {
            Move ttRootMove = Move.fromEncoded(rootEntry.move);
            if (ttRootMove != null && rootMoves.contains(ttRootMove)) {
                fallbackMove = ttRootMove;
            }
        }
        return iterativeDeepening(fallbackMove);
    }

    @Override
    public void stop() {
        this.stopped = true;
    }

    private SearchResult iterativeDeepening(Move fallbackMove) {
        int targetDepth = limits.depth > 0 ? limits.depth : MAX_DEPTH;
        Move bestMove = fallbackMove;
        int bestScore = Score.NONE;
        int depthReached = 0;

        int alpha = -Score.INFINITY;
        int beta = Score.INFINITY;

        for (int depth = 1; depth <= targetDepth; depth++) {

            // Aspiration windows: start from a narrow window around the previous score
            // and widen geometrically on fail-high/fail-low. On repeated fail-high we
            // additionally reduce the searched depth instead of burning full re-searches.
            final int initialWindow = 25;
            if (depth >= 4 && !Score.isMateScore(bestScore)) {
                alpha = bestScore - initialWindow;
                beta = bestScore + initialWindow;
            } else {
                alpha = -Score.INFINITY;
                beta = Score.INFINITY;
            }

            int delta = initialWindow;
            int failHighReduction = 0;
            int score = 0;

            while (true) {
                int searchDepth = Math.max(1, depth - failHighReduction);
                score = alphaBeta(searchDepth, 0, alpha, beta, true, false);

                if (stopped) break;

                boolean failedLow = score <= alpha;
                boolean failedHigh = score >= beta;

                if ((failedLow || failedHigh) && depth >= 4 && !Score.isMateScore(score)) {
                    if (failedHigh) {
                        beta += delta;
                        failHighReduction = Math.min(failHighReduction + 1, 3);
                    } else {
                        beta = (alpha + beta) / 2;
                        alpha -= delta;
                        failHighReduction = 0;
                    }
                    delta += delta / 2;
                    continue;
                }
                break;
            }

            if (stopped && depth > 1) {
                break;
            }

            bestScore = score;
            if (pvTable[0][0] != null) {
                bestMove = pvTable[0][0];
            }
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

    private int alphaBeta(int depth, int ply, int alpha, int beta, boolean allowNull, boolean cutNode) {
        if ((nodes & 2047) == 0 && limits.isTimeUp()) {
            stopped = true;
        }

        if (stopped) return 0;

        nodes++;

        pvLength[ply] = ply;

        boolean isRoot = (ply == 0);
        boolean isPVNode = (beta - alpha > 1);

        if (ply > 0 && isDraw()) {
            return Score.DRAW;
        }

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

        // Mate Distance Pruning — a forced mate closer than the current window makes
        // this node irrelevant; clamp the window and bail early.
        if (!isRoot) {
            alpha = Math.max(alpha, -Score.MATE + ply);
            beta = Math.min(beta, Score.MATE - (ply + 1));
            if (alpha >= beta) return alpha;
        }

        int origAlpha = alpha;
        
        // Transposition Table Probe
        HashEntry ttEntry = tt.probe(board.key(), ply);
        int ttMove = 0;
        
        if (ttEntry != null) {
            ttMove = ttEntry.move;
            if (ttEntry.depth >= depth && !isPVNode) {
                int ttScore = TranspositionTable.getScore(ttEntry, ply);
                if (ttEntry.flag == HashFlag.EXACT.ordinal()) {
                    return ttScore;
                } else if (ttEntry.flag == HashFlag.LOWER_BOUND.ordinal()) {
                    alpha = Math.max(alpha, ttScore);
                } else if (ttEntry.flag == HashFlag.UPPER_BOUND.ordinal()) {
                    beta = Math.min(beta, ttScore);
                }
                
                if (alpha >= beta) {
                    return ttScore;
                }
            }
        }
        
        // Internal Iterative Reductions (replaces the old IID search whose result was
        // discarded): without a TT move the node is expensive to order — just reduce.
        if (!isRoot && ttMove == 0 && (isPVNode || cutNode) && depth >= 4) {
            depth--;
        }

        SearchStack.Entry sse = searchStack.get(ply);

        int staticEval = Evaluator.evaluate(board);
        sse.staticEval = staticEval;

        // Improving heuristic — compare static eval against our previous turn's eval,
        // skipping check plies where no eval was meaningful. An improving position
        // allows more aggressive pruning margins.
        boolean improving = false;
        int lastEval = ply >= 2 ? searchStack.get(ply - 2).staticEval : Score.NONE;
        if (lastEval == Score.NONE && ply >= 4) {
            lastEval = searchStack.get(ply - 4).staticEval;
        }
        improving = lastEval != Score.NONE && staticEval > lastEval;

        // Reverse Futility Pruning (RFP)
        if (!isPVNode && !inCheck && depth <= 3 && !Score.isMateScore(beta)) {
            int rfpMargin = depth * (improving ? 85 : 65);
            if (staticEval - rfpMargin >= beta) {
                return staticEval;
            }
        }

        // Null Move Pruning with adaptive reduction
        boolean hasNonPawnMaterial = board.hasNonPawnMaterial(board.isWhiteToMove());
        if (allowNull && !inCheck && !isPVNode && depth >= 3 && hasNonPawnMaterial
                && staticEval >= beta && !Score.isMateScore(beta)) {
            int R = 2 + depth / 6 + Math.min(2, (staticEval - beta) / 200);
            board.makeNullMove();
            int score = -alphaBeta(depth - 1 - R, ply + 1, -beta, -beta + 1, false, !cutNode);
            board.unmakeNullMove();

            if (stopped) return 0;

            if (score >= beta) {
                return Score.isMateScore(score) ? beta : score;
            }
        }

        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        if (moves.isEmpty()) {
            if (inCheck) {
                return -Score.mateScore(ply);
            } else {
                return Score.DRAW;
            }
        }
        
        int counterMove = 0;
        if (ply > 0) {
            Move prevMove = searchStack.get(ply - 1).currentMove;
            if (prevMove != null) {
                Piece prevPiece = board.pieceAt(prevMove.to());
                if (prevPiece != null) {
                    counterMove = counterMoveTable.get(board.isWhiteToMove(), prevPiece, prevMove.to());
                }
            }
        }

        int[] moveScores = new int[moves.size()];
        MoveOrderer.scoreMoves(moves, moveScores, board, ttMove, killerTable, ply, historyTable, counterMove);

        int movesSearched = 0;
        Move bestMove = null;
        sse.searchedQuiets.clear();
        List<Move> searchedQuiets = sse.searchedQuiets;

        for (int i = 0; i < moves.size(); i++) {
            MoveOrderer.pickMove(moves, moveScores, i);
            Move move = moves.get(i);

            // Futility Pruning
            if (!isPVNode && !inCheck && depth <= 3 && move.isQuiet() && movesSearched > 0 && !Score.isMateScore(alpha) && !Score.isMateScore(beta)) {
                if (staticEval + futilityMargin[depth] <= alpha) {
                    continue; // Skip quiet move
                }
            }

            board.makeMove(move);
            searchStack.get(ply).currentMove = move;

            boolean givesCheck = MoveGenerator.isInCheck(board, board.isWhiteToMove());

            // Late Move Pruning (LMP) — more permissive when improving
            if (!isPVNode && !inCheck && !givesCheck && depth <= 6 && move.isQuiet() && !Score.isMateScore(alpha) && !Score.isMateScore(beta)) {
                int lmpThreshold = (3 + depth * depth) / (improving ? 1 : 2);
                if (movesSearched >= lmpThreshold) {
                    board.unmakeMove();
                    continue; // Skip remaining quiet moves
                }
            }

            // Late Move Reductions (LMR) via precomputed table; never reduce checks
            int reduction = 0;
            if (!isPVNode
                    && depth >= 3
                    && movesSearched >= 2
                    && move.isQuiet()
                    && !inCheck
                    && !givesCheck) {
                reduction = LMR[Math.min(depth, MAX_DEPTH - 1)][Math.min(movesSearched + 1, MAX_DEPTH - 1)];

                int histScore = historyTable.getScore(board.isWhiteToMove(), move.from(), move.to());
                if (histScore > 0) {
                    reduction -= 1;
                } else if (histScore < 0) {
                    reduction += 1;
                }
                reduction = Math.max(0, Math.min(reduction, depth - 2));
            }

            int score;
            if (isPVNode && movesSearched == 0) {
                // Principal Variation Search: first PV move needs the exact score.
                score = -alphaBeta(depth - 1, ply + 1, -beta, -alpha, true, false);
            } else {
                // Null-window scout (possibly reduced).
                score = -alphaBeta(depth - 1 - reduction, ply + 1, -alpha - 1, -alpha, true, true);
                if (score > alpha && (score < beta || reduction > 0)) {
                    // Beat alpha with a reduced/scout search — re-search full depth/window.
                    score = -alphaBeta(depth - 1, ply + 1, -beta, -alpha, true, false);
                }
            }

            board.unmakeMove();

            if (stopped) return 0;

            if (move.isQuiet()) {
                searchedQuiets.add(move);
            }
            movesSearched++;

            if (score > alpha) {
                bestMove = move;
                if (score >= beta) {
                    // Beta Cutoff
                    if (move.isQuiet()) {
                        killerTable.add(move.encoded(), ply);
                        historyTable.addBonus(board.isWhiteToMove(), move.from(), move.to(), depth);

                        // Malus only for quiet moves that were ACTUALLY searched
                        for (Move prevQuiet : searchedQuiets) {
                            if (prevQuiet != move) {
                                historyTable.addMalus(board.isWhiteToMove(), prevQuiet.from(), prevQuiet.to(), depth);
                            }
                        }

                        // Store counter-move
                        if (ply > 0) {
                            Move prevMove = searchStack.get(ply - 1).currentMove;
                            if (prevMove != null) {
                                Piece prevPiece = board.pieceAt(prevMove.to());
                                if (prevPiece != null) {
                                    counterMoveTable.store(board.isWhiteToMove(), prevPiece, prevMove.to(), move.encoded());
                                }
                            }
                        }
                    }

                    HashFlag flag = HashFlag.LOWER_BOUND;
                    tt.store(board.key(), move.encoded(), beta, depth, flag, ply);

                    return beta;
                }
                alpha = score;
                updatePV(ply, move);
            }
        }
        
        HashFlag flag = HashFlag.EXACT;
        if (alpha <= origAlpha) {
            flag = HashFlag.UPPER_BOUND;
        }
        
        int moveEncoded = bestMove != null ? bestMove.encoded() : 0;
        tt.store(board.key(), moveEncoded, alpha, depth, flag, ply);

        return alpha;
    }

    private int quiescence(int alpha, int beta, int ply) {
        if ((nodes & 2047) == 0 && limits.isTimeUp()) {
            stopped = true;
        }

        if (stopped) return 0;

        nodes++;

        if (ply >= MAX_DEPTH - 1) {
            return Evaluator.evaluate(board);
        }

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
        
        int[] moveScores = new int[captures.size()];
        MoveOrderer.scoreMoves(captures, moveScores, board, 0, null, ply, null, 0);

        for (int i = 0; i < captures.size(); i++) {
            MoveOrderer.pickMove(captures, moveScores, i);
            Move move = captures.get(i);
            
            // Quiescence SEE pruning (don't search losing captures)
            if (!SEE.see(board, move, 0)) {
                continue;
            }
            
            board.makeMove(move);
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

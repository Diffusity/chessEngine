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
        
        int alpha = -Score.INFINITY;
        int beta = Score.INFINITY;

        for (int depth = 1; depth <= targetDepth; depth++) {
            int score = alphaBeta(depth, 0, alpha, beta, true);

            // Aspiration window fallback
            if (depth > 4) {
                if (score <= alpha || score >= beta) {
                    alpha = -Score.INFINITY;
                    beta = Score.INFINITY;
                    score = alphaBeta(depth, 0, alpha, beta, true);
                }
            }

            if (stopped && depth > 1) {
                break;
            }

            bestScore = score;
            bestMove = pvTable[0][0];
            depthReached = depth;
            
            // Set up aspiration window for next depth
            if (depth >= 4 && !Score.isMateScore(bestScore)) {
                alpha = bestScore - 25;
                beta = bestScore + 25;
            } else {
                alpha = -Score.INFINITY;
                beta = Score.INFINITY;
            }

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

    private int alphaBeta(int depth, int ply, int alpha, int beta, boolean allowNull) {
        if ((nodes & 2047) == 0 && limits.isTimeUp()) {
            stopped = true;
        }

        if (stopped) return 0;

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
        
        int staticEval = Evaluator.evaluate(board);
        
        // Reverse Futility Pruning (RFP)
        if (!isPVNode && !inCheck && depth <= 3 && !Score.isMateScore(beta)) {
            int rfpMargin = depth * 75;
            if (staticEval - rfpMargin >= beta) {
                return staticEval;
            }
        }

        // Null Move Pruning
        boolean hasNonPawnMaterial = board.hasNonPawnMaterial(board.isWhiteToMove());
        if (allowNull && !inCheck && !isPVNode && depth >= 3 && hasNonPawnMaterial && staticEval >= beta) {
            board.makeNullMove();
            int R = 2; // reduction
            int score = -alphaBeta(depth - 1 - R, ply + 1, -beta, -beta + 1, false);
            board.unmakeNullMove();
            
            if (stopped) return 0;
            
            if (score >= beta) {
                return beta;
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
            nodes++;
            searchStack.get(ply).currentMove = move;
            
            boolean givesCheck = MoveGenerator.isInCheck(board, board.isWhiteToMove());
            
            // Late Move Pruning (LMP)
            if (!isPVNode && !inCheck && !givesCheck && depth <= 4 && move.isQuiet() && !Score.isMateScore(alpha) && !Score.isMateScore(beta)) {
                int lmpThreshold = 3 + depth * depth;
                if (movesSearched >= lmpThreshold) {
                    board.unmakeMove();
                    continue; // Skip remaining quiet moves
                }
            }
            
            int score;
            
            // Late Move Reductions (LMR)
            boolean fullSearch = true;
            if (depth >= 3 && movesSearched >= 2 && move.isQuiet() && !inCheck && !isPVNode) {
                int reduction = (int) (1 + Math.log(depth) * Math.log(movesSearched) / 2.0);
                
                // History-tuned reduction
                int histScore = historyTable.getScore(board.isWhiteToMove(), move.from(), move.to());
                if (histScore > 0) {
                    reduction -= 1; // Good history -> search deeper
                } else if (histScore < 0) {
                    reduction += 1; // Bad history -> reduce more
                }
                
                if (reduction < 0) reduction = 0;
                int reducedDepth = Math.max(0, depth - 1 - reduction);
                
                score = -alphaBeta(reducedDepth, ply + 1, -alpha - 1, -alpha, true);
                if (score > alpha) {
                    fullSearch = true;
                } else {
                    fullSearch = false;
                }
            } else {
                fullSearch = true;
                score = 0; // dummy
            }

            if (fullSearch) {
                // Principal Variation Search
                if (movesSearched == 0) {
                    score = -alphaBeta(depth - 1, ply + 1, -beta, -alpha, true);
                } else {
                    score = -alphaBeta(depth - 1, ply + 1, -alpha - 1, -alpha, true);
                    if (score > alpha && score < beta) {
                        score = -alphaBeta(depth - 1, ply + 1, -beta, -alpha, true);
                    }
                }
            }

            board.unmakeMove();

            if (stopped) return 0;

            if (score > alpha) {
                bestMove = move;
                if (score >= beta) {
                    // Beta Cutoff
                    if (move.isQuiet()) {
                        killerTable.add(move.encoded(), ply);
                        historyTable.addBonus(board.isWhiteToMove(), move.from(), move.to(), depth);
                        
                        // Malus for previous quiet moves
                        for (int j = 0; j < i; j++) {
                            Move prevMove = moves.get(j);
                            if (prevMove.isQuiet()) {
                                historyTable.addMalus(board.isWhiteToMove(), prevMove.from(), prevMove.to(), depth);
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
            movesSearched++;
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

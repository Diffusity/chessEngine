package com.titan.engine;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.movegen.MoveGenerator;
import com.titan.search.SearchLimits;
import com.titan.search.Searcher;
import com.titan.utils.FEN;

import java.util.List;

/**
 * Central coordinator that owns the Board and Searcher.
 * Handles board state updates and manages the search thread.
 */
public class Engine {

    private Board board;
    private final Searcher searcher;
    private Thread searchThread;

    public Engine() {
        this.board = new Board();
        this.searcher = new Searcher(this.board);
        newGame();
    }

    /**
     * Resets the engine to a new game state.
     */
    public void newGame() {
        board = FEN.parse(FEN.STARTING_FEN);
        searcher.getTranspositionTable().clear();
    }

    /**
     * Sets the board position from a FEN string.
     */
    public void setPosition(String fen) {
        board = FEN.parse(fen);
    }

    /**
     * Applies a list of UCI moves (e.g., "e2e4") to the current position.
     * Generates legal moves and matches by string to ensure flags are correct.
     */
    public void applyMoves(List<String> uciMoves) {
        for (String uci : uciMoves) {
            Move legalMove = findLegalMove(board, uci);
            if (legalMove != null) {
                board.makeMove(legalMove);
            } else {
                System.err.println("Warning: Illegal move received: " + uci);
            }
        }
    }

    private Move findLegalMove(Board board, String uci) {
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        for (Move m : legal) {
            if (m.toUCI().equals(uci)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Starts the search on a separate thread.
     */
    public void startSearch(SearchLimits limits) {
        stopSearch(); // Ensure previous search is stopped

        searchThread = new Thread(() -> {
            try {
                // The searcher will handle printing "info" and eventually returning the result.
                // It will NOT print "bestmove" directly according to the Search API,
                // so we will need to print it here or in the UCI handler.
                // Wait, Searcher returns SearchResult. The caller of startSearch can't get it directly
                // because it's a separate thread. We should print "bestmove" when search finishes.
                
                var result = searcher.search(board, limits);
                if (result != null && result.bestMove() != null) {
                    System.out.println("bestmove " + result.bestMove().toUCI());
                } else {
                    System.out.println("bestmove 0000"); // No move found (checkmate/stalemate)
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Signals the search to stop and waits for it to finish.
     */
    public void stopSearch() {
        if (searchThread != null && searchThread.isAlive()) {
            searcher.stop();
            try {
                searchThread.join(2000); // Wait max 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Resizes the transposition table.
     */
    public void setHashSize(int sizeMB) {
        searcher.getTranspositionTable().resize(sizeMB);
    }

    public Board getBoard() {
        return board;
    }

    public Searcher getSearcher() {
        return searcher;
    }
}

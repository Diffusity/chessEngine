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
    private final EngineConfig config;

    public Engine() {
        this.board = new Board();
        this.searcher = new Searcher(this.board);
        this.config = new EngineConfig();
        newGame();
    }

    /**
     * Resets the engine to a new game state.
     */
    public void newGame() {
        setPosition(FEN.STARTING_FEN);
        searcher.getTranspositionTable().clear();
    }

    /**
     * Sets the board position from a FEN string.
     */
    public void setPosition(String fen) {
        Board newBoard = FEN.parse(fen);
        // Copy the state into our existing board instance so Searcher doesn't lose the reference
        this.board = newBoard;
        this.searcher.setBoard(this.board);
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
            while (searchThread.isAlive()) {
                try {
                    // The searcher checks the stop flag every 2048 nodes, so this
                    // loop exits quickly. Waiting unconditionally prevents a new
                    // search from racing a lingering old one on the shared board.
                    searchThread.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Resizes the transposition table.
     */
    public void setHashSize(int sizeMB) {
        searcher.getTranspositionTable().resize(sizeMB);
    }
    
    /**
     * Sets an engine option.
     */
    public void setOption(String name, String value) {
        config.setOption(name, value);
        if (name.equalsIgnoreCase("hash")) {
            setHashSize(config.getHashSizeMB());
        }
    }

    public Board getBoard() {
        return board;
    }

    public Searcher getSearcher() {
        return searcher;
    }
}

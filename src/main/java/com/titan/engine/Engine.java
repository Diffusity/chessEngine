package com.titan.engine;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.movegen.MoveGenerator;
import com.titan.search.SearchLimits;
import com.titan.search.Searcher;
import com.titan.utils.FEN;

import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Central coordinator that owns the Board and Searcher.
 * Handles board state updates and manages the search thread.
 */
public class Engine {

    private Board board;
    private final Searcher searcher;
    private Thread searchThread;
    private final EngineConfig config;
    private final ThreadFactory threadFactory;

    public Engine() {
        this(Thread::new);
    }

    Engine(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.board = new Board();
        this.searcher = new Searcher(this.board);
        this.config = new EngineConfig();
        newGame();
    }

    /**
     * Resets the engine to a new game state.
     */
    public synchronized void newGame() {
        setPosition(FEN.STARTING_FEN);
        searcher.getTranspositionTable().clear();
    }

    /**
     * Sets the board position from a FEN string.
     */
    public synchronized void setPosition(String fen) {
        stopSearch();
        Board newBoard = FEN.parse(fen);
        // The old worker has stopped; both owners can now use the new board.
        this.board = newBoard;
        this.searcher.setBoard(this.board);
    }

    /**
     * Applies a list of UCI moves (e.g., "e2e4") to the current position.
     * Generates legal moves and matches by string to ensure flags are correct.
     */
    public synchronized void applyMoves(List<String> uciMoves) {
        stopSearch();
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
    public synchronized void startSearch(SearchLimits limits) {
        stopSearch(); // Ensure previous search is stopped

        searchThread = threadFactory.newThread(() -> {
            try {
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
    public synchronized void stopSearch() {
        if (searchThread != null && searchThread.isAlive()) {
            searcher.stop();
            searchThread.interrupt();
            try {
                searchThread.join(2000);
                if (searchThread.isAlive()) {
                    throw new IllegalStateException("Search did not stop; shared state was not changed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for search to stop", e);
            }
        }
    }

    /**
     * Resizes the transposition table.
     */
    public synchronized void setHashSize(int sizeMB) {
        stopSearch();
        searcher.getTranspositionTable().resize(sizeMB);
    }
    
    /**
     * Sets an engine option.
     */
    public synchronized void setOption(String name, String value) {
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

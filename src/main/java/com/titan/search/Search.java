package com.titan.search;

import com.titan.board.Board;

/**
 * Interface defining the search contract.
 */
public interface Search {
    int MAX_DEPTH = 128;

    SearchResult search(Board board, SearchLimits limits);
    
    void stop();
}

package com.titan.search;

import com.titan.board.Move;
import com.titan.board.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-allocated per-ply search state.
 */
public class SearchStack {
    
    /**
     * Per-ply entry.
     */
    public static class Entry {
        public int staticEval;
        public Move currentMove;
        public Piece movedPiece;
        public Move[] killers = new Move[2];
        /** Quiets actually searched at this ply — reused across nodes to avoid allocation. */
        public final List<Move> searchedQuiets = new ArrayList<>();
        /** Noisy moves actually searched at this ply (for capture-history malus). */
        public final List<Move> searchedNoisies = new ArrayList<>();

        public void clear() {
            staticEval = Score.NONE;
            currentMove = null;
            movedPiece = null;
            killers[0] = null;
            killers[1] = null;
            searchedQuiets.clear();
            searchedNoisies.clear();
        }
    }

    private final Entry[] entries;

    /** Shared continuation history used by move scoring. Assigned by the Searcher. */
    public com.titan.tables.history.ContinuationHistoryTable continuationHistory;

    public SearchStack(int maxPly) {
        entries = new Entry[maxPly];
        for (int i = 0; i < maxPly; i++) {
            entries[i] = new Entry();
        }
    }

    public Entry get(int ply) {
        return entries[ply];
    }

    public void clear() {
        for (Entry e : entries) {
            e.clear();
        }
    }
}

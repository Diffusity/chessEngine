package com.titan.search;

import com.titan.board.Move;

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
        public Move[] killers = new Move[2];
        /** Quiets actually searched at this ply — reused across nodes to avoid allocation. */
        public final List<Move> searchedQuiets = new ArrayList<>();

        public void clear() {
            staticEval = Score.NONE;
            currentMove = null;
            killers[0] = null;
            killers[1] = null;
            searchedQuiets.clear();
        }
    }

    private final Entry[] entries;

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

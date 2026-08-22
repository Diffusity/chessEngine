package com.titan.tables.tt;

/**
 * A single entry in the Transposition Table.
 */
public class HashEntry {
    public long key;       // full Zobrist key (for collision detection)
    public int move;       // best move (encoded int)
    public int score;      // search score
    public int eval;       // static evaluation (Integer.MIN_VALUE when unknown)
    public short depth;    // search depth when stored
    public byte flag;      // HashFlag ordinal
    public int age;        // search generation (for replacement policy)

    public void clear() {
        key = 0;
        move = 0;
        score = 0;
        eval = Integer.MIN_VALUE;
        depth = 0;
        flag = 0;
        age = 0;
    }
}

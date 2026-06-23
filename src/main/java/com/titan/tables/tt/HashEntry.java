package com.titan.tables.tt;

/**
 * A single entry in the Transposition Table.
 */
public class HashEntry {
    public long key;       // full Zobrist key (for collision detection)
    public int move;       // best move (encoded int)
    public int score;      // search score
    public short depth;    // search depth when stored
    public byte flag;      // HashFlag ordinal
    public byte age;       // search generation (for replacement policy)

    public void clear() {
        key = 0;
        move = 0;
        score = 0;
        depth = 0;
        flag = 0;
        age = 0;
    }
}

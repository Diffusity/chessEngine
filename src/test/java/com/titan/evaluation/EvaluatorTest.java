package com.titan.evaluation;

import com.titan.board.Board;
import com.titan.utils.FEN;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {

    @Test
    @DisplayName("Eval - Starting position is roughly equal")
    void testStartingPosition() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        int score = Evaluator.evaluate(board);
        // It should be exactly 0 due to symmetric piece placement, but occasionally 
        // minor asymmetry in tables could make it slightly off. Ours is perfectly symmetric.
        assertEquals(0, score);
    }

    @Test
    @DisplayName("Eval - Material advantage is reflected")
    void testMaterialAdvantage() {
        // Black is down a full Queen
        String fen = "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        Board board = FEN.parse(fen);
        int score = Evaluator.evaluate(board);

        // White to move, so positive means White is winning.
        // Queen is ~900-1000 centipawns.
        assertTrue(score > 800, "White should be up significantly in score");
    }

    @Test
    @DisplayName("Eval - Score symmetry relative to side to move")
    void testScoreSymmetry() {
        // White up a knight (Black missing b8 knight)
        String fenW = "r1bqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        Board boardW = FEN.parse(fenW);
        int scoreW = Evaluator.evaluate(boardW);
        assertTrue(scoreW > 250, "White should be winning");

        // Same position but black to move
        String fenB = "r1bqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";
        Board boardB = FEN.parse(fenB);
        int scoreB = Evaluator.evaluate(boardB);
        
        // Since black to move, positive score means BLACK is winning.
        // But black is down a knight, so score should be negative.
        assertEquals(-scoreW, scoreB, "Score should be negated when side to move changes");
    }

    @Test
    @DisplayName("Eval - Bishop pair bonus")
    void testBishopPair() {
        // White has 2 bishops, Black has 1 bishop and 1 knight (equal material roughly, but pair bonus)
        String fen = "rn1qkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        Board board = FEN.parse(fen);
        
        int score = Evaluator.evaluate(board);
        assertTrue(score > 0, "White should have an advantage due to the bishop pair");
    }

    @Test
    @DisplayName("Eval - Game Phase calculation")
    void testGamePhase() {
        Board start = FEN.parse(FEN.STARTING_FEN);
        assertEquals(GamePhase.MAX_PHASE, GamePhase.calculate(start));

        // King and pawns endgame
        Board endgame = FEN.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        assertEquals(0, GamePhase.calculate(endgame));
    }

    @Test
    @DisplayName("Eval - Passed Pawn Bonus")
    void testPassedPawn() {
        // White has a passed pawn on e6, Black has none
        String fen = "4k3/8/4P3/8/8/8/8/4K3 w - - 0 1";
        Board board = FEN.parse(fen);
        int score = Evaluator.evaluate(board);
        assertTrue(score > 50, "White should have a significant advantage from the passed pawn");
    }

    @Test
    @DisplayName("Eval - Isolated Pawn Penalty")
    void testIsolatedPawn() {
        // White has an isolated pawn on d4, Black's pawns are connected on e5, f5
        String fen = "4k3/8/8/4pp2/3P4/8/8/4K3 w - - 0 1";
        Board board = FEN.parse(fen);
        int score = Evaluator.evaluate(board);
        assertTrue(score < 0, "White's isolated pawn should result in a negative score");
    }

    @Test
    @DisplayName("Eval - Rook on open file bonus")
    void testRookOpenFile() {
        // White rook on a1 (open file), black rook on h8 (blocked by pawns on h7 and h2)
        String fen = "7r/7p/8/8/8/8/7P/R7 w - - 0 1";
        Board board = FEN.parse(fen);
        int score = Evaluator.evaluate(board);
        assertTrue(score > 0, "White should have advantage due to open file");
    }
}

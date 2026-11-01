package com.titan.movegen;

import com.titan.board.Board;
import com.titan.board.Move;
import com.titan.utils.FEN;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TacticalMoveGeneratorTest {
    private void compare(Board board) {
        String fen = FEN.generate(board);
        long key = board.key();
        var legal = MoveGenerator.generateLegalMoves(board);
        var expected = legal.stream().filter(m -> m.isCapture() || m.isPromotion()).map(Move::toUCI).toList();
        assertEquals(expected, MoveGenerator.generateTacticalMoves(board).stream().map(Move::toUCI).toList(), fen);
        assertEquals(!legal.isEmpty(), MoveGenerator.hasLegalMove(board), fen);
        assertEquals(fen, FEN.generate(board));
        assertEquals(key, board.key());
    }

    @Test void matchesFullGenerationInSpecialPositions() {
        for (String fen : new String[] {
            FEN.STARTING_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "1r2k3/P7/8/8/8/8/7p/4K1R1 w - - 0 1",
            "1r2k3/P7/8/8/8/8/7p/4K1R1 b - - 0 1",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            "k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1",
            "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1",
            "7k/8/5K2/8/8/8/8/7R b - - 0 1"
        }) compare(FEN.parse(fen));
    }

    @Test void matchesFullGenerationAcrossSeededLegalGames() {
        Random random = new Random(20261030);
        for (int game = 0; game < 12; game++) {
            Board board = Board.startingPosition();
            for (int ply = 0; ply < 100; ply++) {
                compare(board);
                var legal = MoveGenerator.generateLegalMoves(board);
                if (legal.isEmpty()) break;
                board.makeMove(legal.get(random.nextInt(legal.size())));
            }
        }
    }

    private static class CountingBoard extends Board {
        int makes;
        CountingBoard() {
            Board source = Board.startingPosition();
            for (int sq = 0; sq < 64; sq++) {
                if (source.pieceAt(sq) != null) addPiece(source.pieceAt(sq), sq, sq < 16);
            }
            setFenState(15, -1, 0);
            finalizeSetup();
        }
        @Override public boolean makeMove(Move move) { makes++; return super.makeMove(move); }
    }

    @Test void tacticalGenerationDoesNotValidateQuietMoves() {
        CountingBoard board = new CountingBoard();
        assertTrue(MoveGenerator.generateTacticalMoves(board).isEmpty());
        assertEquals(0, board.makes, "No tactical moves means no make/unmake legality work");
    }

    @Test void existenceProbeStopsAfterFirstLegalMoveAndRestoresBoard() {
        CountingBoard board = new CountingBoard();
        String fen = FEN.generate(board);
        long key = board.key();
        assertTrue(MoveGenerator.hasLegalMove(board));
        assertEquals(1, board.makes);
        assertEquals(fen, FEN.generate(board));
        assertEquals(key, board.key());
    }
}

package com.titan.movegen;

import com.titan.board.Board;
import com.titan.utils.FEN;
import com.titan.utils.Perft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PerftTest {

    @Test
    public void testStartposDepth4() {
        Board board = FEN.parse(FEN.STARTING_FEN);
        long nodes = Perft.perft(board, 4);
        assertEquals(197281, nodes);
    }

    @ParameterizedTest
    @MethodSource("providePerftPositions")
    public void testPerftSuite(String fen, int depth, long expectedNodes) {
        Board board = FEN.parse(fen);
        long nodes = Perft.perft(board, depth);
        assertEquals(expectedNodes, nodes, "Perft failed for FEN: " + fen);
    }

    private static Stream<Arguments> providePerftPositions() throws Exception {
        List<Arguments> arguments = new ArrayList<>();
        InputStream is = PerftTest.class.getResourceAsStream("/perft_suite.epd");
        if (is == null) {
            System.err.println("Could not find perft_suite.epd");
            return Stream.empty();
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Example: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 ;D5 4865609
                String[] parts = line.split(";");
                String fen = parts[0].trim();
                
                String[] dParts = parts[1].trim().split(" ");
                int depth = Integer.parseInt(dParts[0].substring(1)); // D5 -> 5
                long expectedNodes = Long.parseLong(dParts[1]);

                arguments.add(Arguments.of(fen, depth, expectedNodes));
            }
        }

        return arguments.stream();
    }
}

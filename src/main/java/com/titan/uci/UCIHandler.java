package com.titan.uci;

import com.titan.engine.Engine;
import com.titan.search.SearchLimits;
import com.titan.utils.FEN;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Handles Universal Chess Interface (UCI) protocol communication.
 */
public class UCIHandler {

    private final Engine engine;

    public UCIHandler() {
        this.engine = new Engine();
    }

    /**
     * Starts the main UCI loop, reading from standard input.
     */
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+");
            String command = tokens[0].toLowerCase();

            switch (command) {
                case "uci":
                    handleUCI();
                    break;
                case "isready":
                    handleIsReady();
                    break;
                case "ucinewgame":
                    handleNewGame();
                    break;
                case "position":
                    handlePosition(line);
                    break;
                case "go":
                    handleGo(line);
                    break;
                case "stop":
                    handleStop();
                    break;
                case "setoption":
                    handleSetOption(line);
                    break;
                case "quit":
                    handleQuit();
                    return; // Exit the loop
                default:
                    // Ignore unknown commands per UCI spec
                    break;
            }
        }
    }

    private void handleUCI() {
        send("id name Titan v1.0.0");
        send("id author Titan Team");
        send("option name Hash type spin default 64 min 1 max 2048");
        send("uciok");
    }

    private void handleIsReady() {
        send("readyok");
    }

    private void handleNewGame() {
        engine.newGame();
    }

    private void handlePosition(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) return;

        int movesIndex = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals("moves")) {
                movesIndex = i;
                break;
            }
        }

        // Set position
        if (tokens[1].equals("startpos")) {
            engine.newGame(); // Sets to starting FEN
        } else if (tokens[1].equals("fen")) {
            // Reconstruct FEN string
            StringBuilder fenStr = new StringBuilder();
            int end = movesIndex != -1 ? movesIndex : tokens.length;
            for (int i = 2; i < end; i++) {
                fenStr.append(tokens[i]).append(i < end - 1 ? " " : "");
            }
            try {
                engine.setPosition(fenStr.toString());
            } catch (Exception e) {
                System.err.println("Failed to parse FEN: " + fenStr);
                return;
            }
        }

        // Apply moves if any
        if (movesIndex != -1 && movesIndex + 1 < tokens.length) {
            List<String> moves = new ArrayList<>();
            for (int i = movesIndex + 1; i < tokens.length; i++) {
                moves.add(tokens[i]);
            }
            engine.applyMoves(moves);
        }
    }

    private void handleGo(String line) {
        String[] tokens = line.split("\\s+");
        SearchLimits limits = new SearchLimits();
        
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            try {
                switch (token) {
                    case "wtime":
                        limits.wtime = Long.parseLong(tokens[++i]);
                        break;
                    case "btime":
                        limits.btime = Long.parseLong(tokens[++i]);
                        break;
                    case "winc":
                        limits.winc = Long.parseLong(tokens[++i]);
                        break;
                    case "binc":
                        limits.binc = Long.parseLong(tokens[++i]);
                        break;
                    case "movestogo":
                        limits.movestogo = Integer.parseInt(tokens[++i]);
                        break;
                    case "depth":
                        limits.depth = Integer.parseInt(tokens[++i]);
                        break;
                    case "nodes":
                        limits.nodes = Long.parseLong(tokens[++i]);
                        break;
                    case "movetime":
                        limits.movetime = Long.parseLong(tokens[++i]);
                        break;
                    case "infinite":
                        limits.infinite = true;
                        break;
                }
            } catch (Exception e) {
                // Ignore parse errors for individual parameters
            }
        }

        limits.init(engine.getBoard().isWhiteToMove());
        engine.startSearch(limits);
    }

    private void handleStop() {
        engine.stopSearch();
    }

    private void handleSetOption(String line) {
        String[] tokens = line.split("\\s+");
        // setoption name Hash value <MB>
        if (tokens.length >= 5 && tokens[1].equalsIgnoreCase("name") && tokens[2].equalsIgnoreCase("Hash") && tokens[3].equalsIgnoreCase("value")) {
            try {
                int sizeMB = Integer.parseInt(tokens[4]);
                engine.setHashSize(sizeMB);
            } catch (NumberFormatException e) {
                // Ignore invalid values
            }
        }
    }

    private void handleQuit() {
        engine.stopSearch();
    }

    /**
     * Utility method to print a message to standard output and flush.
     */
    public static void send(String message) {
        System.out.println(message);
        System.out.flush();
    }
}

package com.titan.uci;

import com.titan.engine.Engine;
import com.titan.search.SearchLimits;

import java.util.Scanner;

/**
 * Handles communication via the Universal Chess Interface (UCI) protocol.
 */
public class UCIHandler {

    private final Engine engine;

    public UCIHandler() {
        this.engine = new Engine();
    }

    /**
     * Starts the main UCI loop reading from standard input.
     */
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            UCICommand command = UCICommand.parse(line);

            if (command instanceof UCICommand.UCIInit) {
                handleUCI();
            } else if (command instanceof UCICommand.IsReady) {
                handleIsReady();
            } else if (command instanceof UCICommand.NewGame) {
                handleNewGame();
            } else if (command instanceof UCICommand.Position pos) {
                engine.setPosition(pos.fen());
                engine.applyMoves(pos.moves());
            } else if (command instanceof UCICommand.Go go) {
                go.limits().init(engine.getBoard().isWhiteToMove());
                engine.startSearch(go.limits());
            } else if (command instanceof UCICommand.Stop) {
                engine.stopSearch();
            } else if (command instanceof UCICommand.SetOption opt) {
                engine.setOption(opt.name(), opt.value());
            } else if (command instanceof UCICommand.Quit) {
                engine.stopSearch();
                return; // Exit loop
            }
        }
    }

    private void handleUCI() {
        send("id name Titan v1.0.0");
        send("id author Titan Team");
        send("option name Hash type spin default 64 min 1 max 1024");
        send("option name Threads type spin default 1 min 1 max 1");
        send("uciok");
    }

    private void handleIsReady() {
        send("readyok");
    }

    private void handleNewGame() {
        engine.newGame();
    }

    /**
     * Utility method to print a message to standard output and flush.
     */
    public static void send(String message) {
        System.out.println(message);
        System.out.flush();
    }
}

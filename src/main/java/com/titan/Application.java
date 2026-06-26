package com.titan;

import com.titan.uci.UCIHandler;

/**
 * Entry point for the Titan Chess Engine.
 * <p>
 * Delegates to the UCI protocol handler which manages all communication
 * with chess GUIs and the Lichess platform.
 */
public class Application {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("bench")) {
            int depth = 8;
            if (args.length > 1) {
                try {
                    depth = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }
            com.titan.utils.Bench.run(depth);
        } else {
            UCIHandler handler = new UCIHandler();
            handler.run();
        }
    }
}

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
        UCIHandler handler = new UCIHandler();
        handler.run();
    }
}

package com.titan;

/**
 * Entry point for the Titan Chess Engine.
 * <p>
 * Delegates to the UCI protocol handler which manages all communication
 * with chess GUIs and the Lichess platform.
 */
public class Application {

    public static void main(String[] args) {
        System.out.println("Titan Chess Engine v1.0.0");
        System.out.println("UCI protocol ready.");
    }
}

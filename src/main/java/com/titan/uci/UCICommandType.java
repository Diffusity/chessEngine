package com.titan.uci;

public enum UCICommandType {
    UCI,
    ISREADY,
    UCINEWGAME,
    POSITION,
    GO,
    STOP,
    SETOPTION,
    QUIT,
    UNKNOWN;

    public static UCICommandType from(String token) {
        if (token == null) return UNKNOWN;
        try {
            return valueOf(token.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

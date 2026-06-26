package com.titan.engine;

public class EngineConfig {

    private int hashSizeMB;
    private int threads;

    public EngineConfig() {
        this.hashSizeMB = 64; // default
        this.threads = 1;     // default
    }

    public int getHashSizeMB() {
        return hashSizeMB;
    }

    public void setHashSizeMB(int hashSizeMB) {
        if (hashSizeMB >= 1 && hashSizeMB <= 1024) {
            this.hashSizeMB = hashSizeMB;
        }
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        if (threads >= 1 && threads <= 1) { // 1 thread max for now
            this.threads = threads;
        }
    }

    public void setOption(String name, String value) {
        name = name.toLowerCase();
        try {
            switch (name) {
                case "hash":
                    setHashSizeMB(Integer.parseInt(value));
                    break;
                case "threads":
                    setThreads(Integer.parseInt(value));
                    break;
                default:
                    System.out.println("info string Unsupported option: " + name);
                    break;
            }
        } catch (NumberFormatException e) {
            System.out.println("info string Invalid value for option " + name + ": " + value);
        }
    }
}

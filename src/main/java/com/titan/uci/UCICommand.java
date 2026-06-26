package com.titan.uci;

import com.titan.search.SearchLimits;

import java.util.ArrayList;
import java.util.List;

public sealed interface UCICommand permits 
        UCICommand.UCIInit, 
        UCICommand.IsReady, 
        UCICommand.NewGame, 
        UCICommand.Position, 
        UCICommand.Go, 
        UCICommand.Stop, 
        UCICommand.Quit, 
        UCICommand.SetOption, 
        UCICommand.Unknown {

    record UCIInit() implements UCICommand {}
    record IsReady() implements UCICommand {}
    record NewGame() implements UCICommand {}
    record Position(String fen, List<String> moves) implements UCICommand {}
    record Go(SearchLimits limits) implements UCICommand {}
    record Stop() implements UCICommand {}
    record Quit() implements UCICommand {}
    record SetOption(String name, String value) implements UCICommand {}
    record Unknown(String raw) implements UCICommand {}

    static UCICommand parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new Unknown("");
        }

        String[] tokens = line.trim().split("\\s+");
        UCICommandType type = UCICommandType.from(tokens[0]);

        return switch (type) {
            case UCI -> new UCIInit();
            case ISREADY -> new IsReady();
            case UCINEWGAME -> new NewGame();
            case POSITION -> parsePosition(tokens, line);
            case GO -> parseGo(tokens);
            case STOP -> new Stop();
            case QUIT -> new Quit();
            case SETOPTION -> parseSetOption(tokens);
            case UNKNOWN -> new Unknown(line);
        };
    }

    private static Position parsePosition(String[] tokens, String line) {
        String fen = com.titan.utils.FEN.STARTING_FEN;
        List<String> moves = new ArrayList<>();
        
        int i = 1;
        if (i < tokens.length) {
            if (tokens[i].equals("startpos")) {
                i++;
            } else if (tokens[i].equals("fen")) {
                i++;
                StringBuilder fenBuilder = new StringBuilder();
                while (i < tokens.length && !tokens[i].equals("moves")) {
                    fenBuilder.append(tokens[i]).append(" ");
                    i++;
                }
                fen = fenBuilder.toString().trim();
            }
        }
        
        if (i < tokens.length && tokens[i].equals("moves")) {
            i++;
            while (i < tokens.length) {
                moves.add(tokens[i]);
                i++;
            }
        }
        
        return new Position(fen, moves);
    }

    private static Go parseGo(String[] tokens) {
        SearchLimits limits = new SearchLimits();
        for (int i = 1; i < tokens.length; i++) {
            try {
                switch (tokens[i].toLowerCase()) {
                    case "wtime": limits.wtime = Long.parseLong(tokens[++i]); break;
                    case "btime": limits.btime = Long.parseLong(tokens[++i]); break;
                    case "winc": limits.winc = Long.parseLong(tokens[++i]); break;
                    case "binc": limits.binc = Long.parseLong(tokens[++i]); break;
                    case "movestogo": limits.movestogo = Integer.parseInt(tokens[++i]); break;
                    case "depth": limits.depth = Integer.parseInt(tokens[++i]); break;
                    case "nodes": limits.nodes = Long.parseLong(tokens[++i]); break;
                    case "movetime": limits.movetime = Long.parseLong(tokens[++i]); break;
                    case "infinite": limits.infinite = true; break;
                }
            } catch (Exception e) {
                // Ignore parse errors for individual parameters
            }
        }
        return new Go(limits);
    }

    private static UCICommand parseSetOption(String[] tokens) {
        if (tokens.length >= 3 && tokens[1].equalsIgnoreCase("name")) {
            String name = tokens[2];
            String value = "";
            if (tokens.length >= 5 && tokens[3].equalsIgnoreCase("value")) {
                value = tokens[4];
            }
            return new SetOption(name, value);
        }
        return new Unknown(String.join(" ", tokens));
    }
}

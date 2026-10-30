import com.titan.board.Board;
import com.titan.search.SearchLimits;
import com.titan.search.SearchResult;
import com.titan.search.Searcher;
import com.titan.utils.FEN;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

/** Standalone fixed-depth workload. Compile/run against a frozen engine JAR. */
public class ProfileSearch {
    private static final String[] POSITIONS = {
        FEN.STARTING_FEN,
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
    };

    public static void main(String[] args) throws Exception {
        PrintStream output = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        Searcher searcher = new Searcher(FEN.parse(POSITIONS[0]));
        // Warm up the JVM before measuring. Reuse the worker but clear its TT.
        for (int round = 0; round < 3; round++) {
            for (String fen : POSITIONS) run(searcher, fen, 6);
        }
        boolean profile = args.length > 0;
        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            if (profile) recording.start();
            output.println("round,position,depth,nodes,time_ms,score,bestmove");
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < POSITIONS.length; i++) {
                    SearchResult result = run(searcher, POSITIONS[i], 8);
                    output.printf("%d,%d,%d,%d,%d,%d,%s%n", round, i, result.depth(),
                                  result.nodes(), result.timeMs(), result.score(), result.bestMove());
                }
            }
            if (profile) {
                recording.stop();
                recording.dump(Path.of(args[0]));
            }
        }
    }

    private static SearchResult run(Searcher searcher, String fen, int depth) {
        Board board = FEN.parse(fen);
        searcher.setBoard(board);
        searcher.getTranspositionTable().clear();
        SearchLimits limits = SearchLimits.fixedDepth(depth);
        limits.init(board.isWhiteToMove());
        return searcher.search(board, limits);
    }
}

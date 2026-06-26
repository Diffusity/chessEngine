package com.titan.uci;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UCITest {

    @Test
    public void testParseUCI() {
        UCICommand cmd = UCICommand.parse("uci");
        assertTrue(cmd instanceof UCICommand.UCIInit);
    }

    @Test
    public void testParseIsReady() {
        UCICommand cmd = UCICommand.parse("isready");
        assertTrue(cmd instanceof UCICommand.IsReady);
    }

    @Test
    public void testParsePositionStartpos() {
        UCICommand cmd = UCICommand.parse("position startpos moves e2e4 e7e5");
        assertTrue(cmd instanceof UCICommand.Position);
        UCICommand.Position pos = (UCICommand.Position) cmd;
        assertEquals(com.titan.utils.FEN.STARTING_FEN, pos.fen());
        assertEquals(2, pos.moves().size());
        assertEquals("e2e4", pos.moves().get(0));
        assertEquals("e7e5", pos.moves().get(1));
    }

    @Test
    public void testParsePositionFEN() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        UCICommand cmd = UCICommand.parse("position fen " + fen + " moves e7e5");
        assertTrue(cmd instanceof UCICommand.Position);
        UCICommand.Position pos = (UCICommand.Position) cmd;
        assertEquals(fen, pos.fen());
        assertEquals(1, pos.moves().size());
        assertEquals("e7e5", pos.moves().get(0));
    }

    @Test
    public void testParseGo() {
        UCICommand cmd = UCICommand.parse("go wtime 300000 btime 300000 winc 0 binc 0 depth 10");
        assertTrue(cmd instanceof UCICommand.Go);
        UCICommand.Go go = (UCICommand.Go) cmd;
        assertEquals(300000, go.limits().wtime);
        assertEquals(300000, go.limits().btime);
        assertEquals(10, go.limits().depth);
    }

    @Test
    public void testParseSetOption() {
        UCICommand cmd = UCICommand.parse("setoption name Hash value 128");
        assertTrue(cmd instanceof UCICommand.SetOption);
        UCICommand.SetOption opt = (UCICommand.SetOption) cmd;
        assertEquals("Hash", opt.name());
        assertEquals("128", opt.value());
    }
}

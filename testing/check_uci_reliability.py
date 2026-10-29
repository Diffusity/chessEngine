"""Local process-level UCI regressions. Run after mvn package; no network needed."""
import argparse
import json
from pathlib import Path
from measure_uci import Engine

WHITE_START_MOVES = {f"{file}2{file}{rank}" for file in "abcdefgh" for rank in "34"}
WHITE_START_MOVES |= {"b1a3", "b1c3", "g1f3", "g1h3"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output filename")
    results = []
    engine = Engine("java", args.jar.resolve(strict=True))
    try:
        engine.send("uci")
        engine.until("uciok", 10)
        engine.send("isready")
        engine.until("readyok", 10)

        # Many back-to-back cancellations in the same JVM exercise worker reuse.
        for index in range(20):
            engine.send("ucinewgame")
            engine.send("position startpos")
            engine.send("go infinite")
            engine.send("stop")
            lines = engine.until("bestmove ", 5)
            move = lines[-1].split()[1]
            assert move in WHITE_START_MOVES, lines
            engine.send("isready")
            ready = engine.until("readyok", 5)
            assert not any(line.startswith("bestmove ") for line in ready), ready
            results.append({"case": "immediate_stop", "iteration": index, "bestmove": move})

        for command in ["go wtime 0 btime 0", "go movetime 0", "go wtime 1 btime 1 winc 2000 binc 2000"]:
            engine.send("position startpos")
            engine.send(command)
            lines = engine.until("bestmove ", 5)
            assert lines[-1].split()[1] in WHITE_START_MOVES, lines
            results.append({"case": command, "transcript": lines})

        for name, fen in [
            ("mate", "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"),
            ("stalemate", "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1"),
        ]:
            engine.send("position fen " + fen)
            engine.send("go movetime 0")
            lines = engine.until("bestmove ", 5)
            assert lines[-1] == "bestmove 0000", lines
            results.append({"case": name, "transcript": lines})
    finally:
        engine.close()
    assert not engine.stderr, engine.stderr
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"jar": str(args.jar), "passed": len(results), "cases": results}, indent=2))
    print(f"Passed {len(results)} process-level UCI checks")


if __name__ == "__main__":
    main()

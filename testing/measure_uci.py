"""Bounded, local-only UCI latency measurements. Python standard library only.

These are diagnostics, not Elo tests or independent move-legality tests.
Each sample gets a new process; a second search measures the reused process.
ucinewgame clears the TT between searches, but not all engine/JVM state.
"""
import argparse
import hashlib
import json
import platform
import queue
import subprocess
import threading
import time
from pathlib import Path

POSITIONS = {
    "start": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "kiwipete": "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "rook_endgame": "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
}
COMMANDS = {
    "fixed_100ms": "go movetime 100",
    "fixed_1000ms": "go movetime 1000",
    "clock_3plus2": "go wtime 180000 btime 180000 winc 2000 binc 2000",
    "low_clock": "go wtime 1000 btime 1000 winc 2000 binc 2000",
}


class Engine:
    def __init__(self, java, jar):
        self.lines = queue.Queue()
        self.stderr = []
        self.process = subprocess.Popen(
            [java, "-jar", str(jar)], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, bufsize=1,
        )
        def read_stdout():
            for line in self.process.stdout:
                self.lines.put(line.rstrip())
            self.lines.put(None)
        def read_stderr():
            self.stderr.extend(self.process.stderr)
        self.readers = [threading.Thread(target=read_stdout, daemon=True),
                        threading.Thread(target=read_stderr, daemon=True)]
        for reader in self.readers:
            reader.start()

    def send(self, command):
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()

    def until(self, prefix, timeout):
        deadline = time.perf_counter() + timeout
        lines = []
        while True:
            left = deadline - time.perf_counter()
            if left <= 0:
                raise TimeoutError(f"No {prefix} within {timeout}s")
            try:
                line = self.lines.get(timeout=left)
            except queue.Empty:
                raise TimeoutError(f"No {prefix} within {timeout}s") from None
            if line is None:
                raise RuntimeError("Engine exited before " + prefix)
            lines.append(line)
            if line.startswith(prefix):
                return lines

    def close(self):
        if self.process.poll() is None:
            try:
                self.send("quit")
                self.process.wait(timeout=3)
            except (subprocess.TimeoutExpired, BrokenPipeError, OSError):
                self.process.kill()
                self.process.wait()
        for reader in self.readers:
            reader.join(timeout=1)
        for stream in [self.process.stdin, self.process.stdout, self.process.stderr]:
            stream.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--repeats", type=int, default=1)
    parser.add_argument("--modes", nargs="+", choices=COMMANDS, default=list(COMMANDS))
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("--repeats must be positive")
    if args.output.exists():
        parser.error("Output exists; choose a new filename to preserve evidence")
    jar = args.jar.resolve(strict=True)
    report = {
        "jar": str(jar), "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "platform": platform.platform(), "processor": platform.processor(),
        "java": subprocess.run([args.java, "-version"], capture_output=True, text=True).stderr,
        "hash_mb": 64, "positions": POSITIONS, "commands": COMMANDS,
        "note": "Local UCI wall times; no network, bridge, full games, or Elo measurement. "
                "First/subsequent are JVM search order, not equivalent TT/history states.",
        "samples": [],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    failed = False
    for repeat in range(args.repeats):
        for name, fen in POSITIONS.items():
            for mode in args.modes:
                engine = None
                sample = None
                started = time.perf_counter()
                try:
                    engine = Engine(args.java, jar)
                    engine.send("uci")
                    engine.until("uciok", 10)
                    engine.send("isready")
                    engine.until("readyok", 10)
                    startup_ms = (time.perf_counter() - started) * 1000
                    for search_order in ["first", "subsequent"]:
                        sample = {"repeat": repeat, "position": name, "mode": mode,
                                  "search_order": search_order, "startup_ms": startup_ms}
                        report["samples"].append(sample)
                        engine.send("ucinewgame")
                        engine.send("position fen " + fen)
                        engine.send("isready")
                        engine.until("readyok", 10)
                        started = time.perf_counter()
                        engine.send(COMMANDS[mode])
                        lines = engine.until("bestmove ", 35)
                        sample["wall_ms"] = round((time.perf_counter() - started) * 1000, 3)
                        sample["transcript"] = lines
                        sample["bestmove"] = lines[-1].split()[1]
                        info = next((line for line in reversed(lines) if line.startswith("info depth ")), "")
                        fields = info.split()
                        for field in ["depth", "nodes", "time", "nps"]:
                            if field in fields:
                                sample[field] = int(fields[fields.index(field) + 1])
                        if sample["bestmove"] == "0000":
                            sample["error"] = "Null move in a known nonterminal test position"
                            failed = True
                        print(name, mode, search_order, sample["wall_ms"], sample["bestmove"], flush=True)
                except (TimeoutError, RuntimeError, OSError) as error:
                    if sample is None:
                        sample = {"repeat": repeat, "position": name, "mode": mode}
                        report["samples"].append(sample)
                    sample["error"] = str(error)
                    failed = True
                    print("ERROR", name, mode, str(error), flush=True)
                finally:
                    if engine is not None:
                        engine.close()
                        if engine.stderr and sample is not None:
                            sample["stderr"] = "".join(engine.stderr)
                    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()

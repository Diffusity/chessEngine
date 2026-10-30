"""Run an isolated, reproducible local 3+2 diagnostic match (no network)."""
import argparse
import hashlib
import json
import platform
import shutil
import subprocess
from pathlib import Path


def command(fastchess, java, pairs):
    return [str(fastchess),
            "-engine", f"cmd={java}", "name=Candidate", "args=-Xmx512m -jar candidate.jar",
            "-engine", f"cmd={java}", "name=Baseline", "args=-Xmx512m -jar baseline.jar",
            "-each", "proto=uci", "tc=180+2", "timemargin=0", "option.Hash=64",
            "-rounds", str(pairs), "-games", "2", "-concurrency", "1",
            "-srand", "20261030", "-autosaveinterval", "1",
            "-config", "outname=state.json", "-pgnout", "file=games.pgn",
            "append=false", "nodes=true", "nps=true", "timeleft=true", "latency=true",
            "-log", "file=engine.log", "level=trace", "engine=true", "append=false",
            "-output", "format=cutechess"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["candidate", "baseline", "fastchess", "output"]:
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--java", default="java.exe")
    parser.add_argument("--pairs", type=int, default=10)
    args = parser.parse_args()
    if args.pairs < 1:
        parser.error("pairs must be positive")
    java = shutil.which(args.java)
    if not java:
        parser.error("Java not found")
    source = {name: getattr(args, name).resolve(strict=True) for name in ["candidate", "baseline", "fastchess"]}
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    hashes = {}
    for name in ["candidate", "baseline"]:
        shutil.copy2(source[name], output / (name + ".jar"))
        hashes[name] = hashlib.sha256(source[name].read_bytes()).hexdigest()
    cmd = command(source["fastchess"], java, args.pairs)
    manifest = {"command": cmd, "sources": {k: str(v) for k,v in source.items()},
                "sha256": hashes, "fastchess_sha256": hashlib.sha256(source["fastchess"].read_bytes()).hexdigest(),
                "java": subprocess.run([java, "-version"], capture_output=True, text=True, timeout=15).stderr,
                "platform": platform.platform(), "pairs": args.pairs, "expected_games": args.pairs*2,
                "concurrency": 1, "time_control": "180+2", "adjudication": "disabled",
                "openings": "standard starting position; no book or external analysis",
                "status": "running"}
    path = output / "manifest.json"
    path.write_text(json.dumps(manifest, indent=2))
    with (output / "console.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(cmd, cwd=output, stdout=log, stderr=subprocess.STDOUT)
        manifest["pid"] = process.pid
        path.write_text(json.dumps(manifest, indent=2))
        returncode = process.wait()
    manifest["exit_code"] = returncode
    manifest["status"] = "finished" if returncode == 0 else "failed"
    path.write_text(json.dumps(manifest, indent=2))
    print(json.dumps({"output": str(output), "status": manifest["status"], "exit_code": returncode}))
    raise SystemExit(returncode)


if __name__ == "__main__":
    main()

"""Run an isolated, reproducible local 3+2 diagnostic match (no network)."""
import argparse
import hashlib
import json
import platform
import shutil
import subprocess
from pathlib import Path
import os
from match_guard import MatchGuard


def command(fastchess, java, pairs, openings=None, candidate_full_soft_clock=False):
    experimental = '-Dtitan.experimental.fullSoftClock=true ' if candidate_full_soft_clock else ''
    result = [str(fastchess),
            "-engine", f"cmd={java}", "name=Candidate", f"args=-Xmx512m {experimental}-jar candidate.jar",
            "-engine", f"cmd={java}", "name=Baseline", "args=-Xmx512m -jar baseline.jar",
            "-each", "proto=uci", "tc=180+2", "timemargin=0", "option.Hash=64",
            "-rounds", str(pairs), "-games", "2", "-concurrency", "1",
            "-srand", "20261030", "-autosaveinterval", "1",
            "-config", "outname=state.json", "-pgnout", "file=games.pgn",
            "append=false", "nodes=true", "nps=true", "timeleft=true", "latency=true",
            "-log", "file=engine.log", "level=trace", "engine=true", "append=false",
            "-output", "format=cutechess"]
    if openings:
        result += ['-openings', f'file={openings}', 'format=epd', 'order=sequential']
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["candidate", "baseline", "fastchess", "output"]:
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--java", default="java.exe")
    parser.add_argument("--pairs", type=int, default=10)
    parser.add_argument("--openings", type=Path)
    parser.add_argument("--candidate-full-soft-clock", action='store_true')
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
    openings = None
    if args.openings:
        openings = args.openings.resolve(strict=True)
        shutil.copy2(openings, output / 'openings.epd')
        openings = output / 'openings.epd'
    cmd = command(source["fastchess"], java, args.pairs, openings, args.candidate_full_soft_clock)
    manifest = {"command": cmd, "sources": {k: str(v) for k,v in source.items()},
                "sha256": hashes, "fastchess_sha256": hashlib.sha256(source["fastchess"].read_bytes()).hexdigest(),
                "java": subprocess.run([java, "-version"], capture_output=True, text=True, timeout=15).stderr,
                "platform": platform.platform(), "pairs": args.pairs, "expected_games": args.pairs*2,
                "concurrency": 1, "time_control": "180+2", "adjudication": "disabled",
                "openings": "standard starting position; no book or external analysis",
                "status": "running"}
    path = output / "manifest.json"
    manifest['candidate_time_policy'] = 'experimental-full-soft-clock' if args.candidate_full_soft_clock else 'default'
    if openings:
        manifest['openings'] = 'Local test positions only; not an engine opening book'
        manifest['openings_sha256'] = hashlib.sha256(openings.read_bytes()).hexdigest()
    manifest['guard'] = 'Temporary idle-sleep prevention, AC required, one-second clock sampling; gaps >5s invalidate run'
    path.write_text(json.dumps(manifest, indent=2))
    process = None
    try:
        with MatchGuard() as guard, (output / "console.log").open("w", encoding="utf-8") as log, (output / 'clock.jsonl').open('w') as clock_log:
            process = subprocess.Popen(cmd, cwd=output, stdout=log, stderr=subprocess.STDOUT)
            manifest["pid"] = process.pid
            path.write_text(json.dumps(manifest, indent=2))
            returncode = guard.wait(process, clock_log)
    except BaseException as error:
        if process is not None and process.poll() is None:
            if os.name == 'nt':
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            else:
                process.kill()
            process.wait(timeout=10)
        manifest.update(status='invalid_or_interrupted', error=str(error))
        path.write_text(json.dumps(manifest, indent=2))
        raise
    manifest["exit_code"] = returncode
    manifest["status"] = "finished" if returncode == 0 else "failed"
    path.write_text(json.dumps(manifest, indent=2))
    print(json.dumps({"output": str(output), "status": manifest["status"], "exit_code": returncode}))
    raise SystemExit(returncode)


if __name__ == "__main__":
    main()

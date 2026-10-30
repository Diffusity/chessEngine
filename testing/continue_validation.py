"""Resume the frozen diagnostic match, then test the tactical candidate at 3+2.

Run with the bridge venv (python-chess). Jobs run sequentially to avoid CPU
contention. A status file and PGNs remain available if the terminal disconnects.
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from analyse_match import analyse


def resume_command(fastchess):
    return [str(fastchess), "-config", "file=state.json", "stats=true",
            "outname=state.json", "-log", "file=engine-resumed.log",
            "level=trace", "engine=true", "append=true"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["match-dir", "followup-dir", "candidate", "fastchess", "status"]:
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--java", required=True)
    args = parser.parse_args()
    directory = args.match_dir.resolve(strict=True)
    followup = args.followup_dir.resolve()
    candidate = args.candidate.resolve(strict=True)
    fastchess = args.fastchess.resolve(strict=True)
    status_path = args.status.resolve()
    if status_path.exists() or followup.exists():
        parser.error("Choose fresh status and followup paths to avoid duplicate runs")
    manifest_path = directory / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    for name in ["candidate", "baseline"]:
        actual = hashlib.sha256((directory / (name + ".jar")).read_bytes()).hexdigest()
        if actual != manifest["sha256"][name]:
            raise ValueError("Frozen binary changed: " + name)
    if hashlib.sha256(fastchess.read_bytes()).hexdigest() != manifest["fastchess_sha256"]:
        raise ValueError("Fastchess binary changed")
    original_state = (directory / "state.json").read_text()
    state = json.loads(original_state)
    assert state["concurrency"] == 1 and state["games"] == 2 and state["rounds"] == 10
    assert all(not state[k]["enabled"] for k in ["resign", "draw", "maxmoves", "tb_adjudication"])
    for engine in state["engines"]:
        assert engine["limit"]["tc"]["time"] == 180000
        assert engine["limit"]["tc"]["increment"] == 2000
    with (directory / "state-before-resume.json").open("x") as backup:
        backup.write(original_state)
    status = {"phase": "resuming_baseline", "completed": False}
    def save():
        status_path.parent.mkdir(parents=True, exist_ok=True)
        status_path.write_text(json.dumps(status, indent=2))
    save()
    try:
        with (directory / "console-resumed.log").open("a") as log:
            process = subprocess.Popen(resume_command(fastchess), cwd=directory,
                                       stdout=log, stderr=subprocess.STDOUT)
            status["match_pid"] = process.pid
            manifest.update(status="running", pid=process.pid, resumed=True)
            manifest_path.write_text(json.dumps(manifest, indent=2))
            save()
            if process.wait() != 0:
                raise RuntimeError("Resumed match failed; see console-resumed.log")
        report = analyse(directory / "games.pgn")
        (directory / "summary.json").write_text(json.dumps(report, indent=2))
        if report["completed_games"] != 20:
            raise RuntimeError("Expected exactly 20 completed baseline games")
        manifest.update(status="finished", completed_games=20)
        manifest_path.write_text(json.dumps(manifest, indent=2))
        status.update(phase="tactical_followup", baseline=report)
        save()
        subprocess.run([sys.executable, str(Path(__file__).with_name("run_match.py")),
                        "--candidate", str(candidate), "--baseline", str(directory / "candidate.jar"),
                        "--fastchess", str(fastchess), "--java", args.java,
                        "--pairs", "10", "--output", str(followup)], check=True)
        report = analyse(followup / "games.pgn")
        (followup / "summary.json").write_text(json.dumps(report, indent=2))
        if report["completed_games"] != 20:
            raise RuntimeError("Expected exactly 20 completed tactical games")
        status.update(phase="finished", completed=True, tactical=report)
        save()
    except BaseException as error:
        status.update(phase="failed_or_interrupted", error=str(error))
        save()
        raise


if __name__ == "__main__":
    main()

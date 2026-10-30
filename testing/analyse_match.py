"""Summarise completed PGNs using rules only; no external engine analysis."""
import argparse
import collections
import json
import re
from pathlib import Path
import chess.pgn


def analyse(path):
    games = []
    totals = collections.Counter()
    with path.open(encoding="utf-8") as stream:
        while (game := chess.pgn.read_game(stream)) is not None:
            if game.headers.get("Result") not in {"1-0", "0-1", "1/2-1/2"}:
                continue
            if game.errors:
                raise ValueError(f"Invalid PGN: {game.errors}")
            board = game.board()
            times = collections.defaultdict(list)
            clocks = collections.defaultdict(list)
            speeds = collections.defaultdict(list)
            plies = 0
            for node in game.mainline():
                side = game.headers["White" if board.turn else "Black"]
                if node.move not in board.legal_moves:
                    raise ValueError(f"Illegal recorded move in game {len(games)+1}")
                match = re.search(r"/\d+\s+([\d.]+)s", node.comment)
                if match: times[side].append(float(match.group(1)))
                match = re.search(r"tl=([\d.]+)s", node.comment)
                if match: clocks[side].append(float(match.group(1)))
                match = re.search(r"nps=(\d+)", node.comment)
                if match: speeds[side].append(int(match.group(1)))
                board.push(node.move)
                plies += 1
            result = game.headers["Result"]
            white_candidate = game.headers["White"] == "Candidate"
            outcome = "draw" if result == "1/2-1/2" else "win" if (result == "1-0") == white_candidate else "loss"
            totals[outcome] += 1
            games.append({"number": len(games)+1, "white": game.headers["White"],
                          "black": game.headers["Black"], "result": result,
                          "candidate_outcome": outcome, "plies": plies,
                          "termination": game.headers.get("Termination"),
                          "checkmate": board.is_checkmate(), "final_fen": board.fen(),
                          "max_move_seconds": {s:max(v) for s,v in times.items()},
                          "min_recorded_clock_seconds": {s:min(v) for s,v in clocks.items()},
                          "mean_reported_nps": {s:round(sum(v)/len(v)) for s,v in speeds.items()}})
    return {"completed_games":len(games), "candidate":dict(totals),
            "terminations":dict(collections.Counter(g["termination"] for g in games)),
            "games":games,
            "limitations":"Small diagnostic batch from startpos; repeated games are correlated. No Elo/strength conclusion."}


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pgn",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    report=analyse(args.pgn)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='games'}))

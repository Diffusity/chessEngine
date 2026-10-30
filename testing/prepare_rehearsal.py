"""Prepare a private lichess-bot config for one explicitly authorised opponent.

Run with the bridge's Python environment (PyYAML). Never prints credentials.
The original config remains intact; launch the bridge with the generated config.
"""
import argparse
import copy
import os
from pathlib import Path
import yaml


def rehearsal_config(original, opponent, base, increment, engine_dir, engine_name):
    if not opponent.strip():
        raise ValueError("An authorised opponent is required")
    if (base, increment) not in {(180, 2), (600, 5)}:
        raise ValueError("Only the official controls are supported")
    cfg = copy.deepcopy(original)
    cfg.setdefault("challenge", {}).update({
        "variants": ["standard"], "modes": ["casual"],
        "time_controls": ["blitz" if base == 180 else "rapid"],
        "min_base": base, "max_base": base,
        "min_increment": increment, "max_increment": increment,
        "concurrency": 1, "accept_bot": True, "only_bot": False,
        "allow_list": [opponent],
    })
    cfg.setdefault("matchmaking", {})["allow_matchmaking"] = False
    engine = cfg.setdefault("engine", {})
    engine.update({"dir": engine_dir, "name": engine_name, "interpreter": "java",
                   "interpreter_options": ["-jar"], "protocol": "uci"})
    # Disable every configured source of moves other than the UCI engine.
    def disable(section):
        if isinstance(section, dict):
            if "enabled" in section:
                section["enabled"] = False
            for value in section.values():
                disable(value)
    for name in ["polyglot", "online_moves", "lichess_bot_tbs"]:
        disable(engine.get(name, {}))
    return cfg


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bridge", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--opponent", required=True)
    parser.add_argument("--control", choices=["3+2", "10+5"], required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output path; private configurations are not overwritten")
    jar = args.jar.resolve(strict=True)
    bridge = args.bridge.resolve(strict=True)
    original = yaml.safe_load((bridge / "config.yml").read_text())
    base, increment = (180, 2) if args.control == "3+2" else (600, 5)
    result = rehearsal_config(original, args.opponent, base, increment,
                              os.path.relpath(jar.parent, bridge), jar.name)
    result["engine"]["interpreter"] = args.java
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(yaml.safe_dump(result, sort_keys=False))
    print("Private rehearsal configuration prepared; credential values were not printed.")


if __name__ == "__main__":
    main()

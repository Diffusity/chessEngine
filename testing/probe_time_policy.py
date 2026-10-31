"""Probe both timing policies at the clocks preceding three recorded loss moves."""
import argparse
import json
import re
from pathlib import Path
import chess.pgn
from deep_loss_review import probe


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java',required=True); p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    cases=json.loads(Path('artifacts/2026-10-31/loss-cases.json').read_text())
    with Path(cases[0]['source']).open() as stream:
        games=[]
        while (game:=chess.pgn.read_game(stream)) is not None: games.append(game)
    results=[]
    for case in cases:
        clocks={True:180000,False:180000}; game=games[case['game']-1]; board=game.board()
        for node in game.mainline():
            if node.ply()==len(case['before_drop']['moves'])+1: break
            match=re.search(r'tl=([\d.]+)s',node.comment)
            if match: clocks[board.turn]=round(float(match[1])*1000)
            board.push(node.move)
        assert board.fen()==case['before_drop']['fen']
        command=f'go wtime {clocks[True]} btime {clocks[False]} winc 2000 binc 2000'
        rows=[]
        for experimental in [False,True]:
            result=probe(args.java,args.jar,case['initial_fen'],case['before_drop']['moves'],command,
                         ['-Dtitan.experimental.fullSoftClock=true'] if experimental else [])
            result['experimental']=experimental; rows.append(result)
        results.append({'game':case['game'],'ply':case['before_drop']['ply'],'clock_command':command,'probes':rows})
        args.output.write_text(json.dumps(results,indent=2))
        print(json.dumps(results[-1]),flush=True)


if __name__=='__main__': main()

"""Compare our three frozen builds at equal depth/time on logged loss positions.

The first recorded >=1 pawn deterioration between a candidate's turns is a
diagnostic lead, not an oracle proving which move was a mistake.
"""
import argparse
import hashlib
import json
import re
import time
from pathlib import Path
import chess.pgn
from measure_uci import Engine


def select(path, numbers):
    selected=[]
    with path.open() as stream:
        number=0
        while (game:=chess.pgn.read_game(stream)) is not None:
            number+=1
            if number not in numbers: continue
            assert game.headers['Termination']=='normal'
            board=game.board(); history=[]; previous=None; found=None
            for node in game.mainline():
                candidate=game.headers['White' if board.turn else 'Black']=='Candidate'
                match=re.match(r'([+-]?\d+\.\d+)/',node.comment)
                if candidate and match:
                    score=float(match.group(1))
                    now={'fen':board.fen(),'moves':list(history),'played':node.move.uci(),
                         'ply':len(history)+1,'logged_score_pawns':score,'comment':node.comment}
                    if previous and score<=previous['logged_score_pawns']-1 and previous['logged_score_pawns']>-2:
                        found={'source':str(path),'game':number,'initial_fen':game.board().fen(),'before_drop':previous,'after_drop':now}
                        break
                    previous=now
                history.append(node.move.uci()); board.push(node.move)
            if found: selected.append(found)
    return selected


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    jars={'original':Path('artifacts/2026-10-29/baseline/titan-baseline.jar'),
          'reliability':Path('artifacts/2026-10-29/reliability-candidate/titan-reliability.jar'),
          'tactical':Path('artifacts/2026-10-30/tactical-candidate/titan-tactical.jar')}
    cases=select(Path('artifacts/matches/2026-10-30-baseline-3plus2-run2/games.pgn'),{1,4})
    cases+=select(Path('artifacts/matches/2026-10-30-tactical-3plus2/games.pgn'),{5,18})
    report={'method':'First logged one-pawn drop between candidate turns; own-engine diagnosis only. Equal depth 10 and equal requested time 1000ms. Full game history retained. Fresh process, depth-5 warmup, then ucinewgame per sample. Not proof of optimal moves.',
            'sha256':{k:hashlib.sha256(v.read_bytes()).hexdigest() for k,v in jars.items()},'cases':cases}
    for case in cases:
        case['comparisons']=[]
        position=case['before_drop']
        for name,jar in jars.items():
            for command in ['go depth 10','go movetime 1000']:
                engine=Engine(args.java,jar.resolve())
                try:
                    engine.send('uci'); engine.until('uciok',10)
                    engine.send('position startpos'); engine.send('go depth 5'); engine.until('bestmove ',20)
                    engine.send('ucinewgame'); engine.send('isready'); engine.until('readyok',10)
                    engine.send('position fen '+case['initial_fen']+' moves '+' '.join(position['moves']))
                    start=time.perf_counter(); engine.send(command)
                    lines=engine.until('bestmove ',60)
                    last=next((line for line in reversed(lines) if line.startswith('info depth ')),None)
                    move=lines[-1].split()[1]
                    assert chess.Move.from_uci(move) in chess.Board(position['fen']).legal_moves
                    case['comparisons'].append({'build':name,'command':command,'elapsed_ms':round((time.perf_counter()-start)*1000),'bestmove':move,'info':last})
                finally: engine.close()
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(report,indent=2))
        print(json.dumps({'game':case['game'],'ply':position['ply'],'comparisons':case['comparisons']}),flush=True)


if __name__=='__main__': main()

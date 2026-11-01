"""Bounded root and forced-move diagnosis using only our own frozen engines."""
import argparse
import json
import re
import time
import hashlib
from pathlib import Path
import chess
from measure_uci import Engine


def probe(java, jar, initial, moves, command, java_options=None):
    engine=Engine(java,jar.resolve(),java_options)
    try:
        engine.send('uci'); engine.until('uciok',10)
        engine.send('position startpos'); engine.send('go depth 5'); engine.until('bestmove ',20)
        engine.send('ucinewgame'); engine.send('isready'); engine.until('readyok',10)
        engine.send('position fen '+initial+' moves '+' '.join(moves))
        started=time.perf_counter(); engine.send(command)
        lines=engine.until('bestmove ',25)
        info=next((x for x in reversed(lines) if x.startswith('info depth ')), '')
        depth=re.search(r'depth (\d+)',info); score=re.search(r'score (cp|mate) (-?\d+)',info)
        result={'command':command,'elapsed_ms':round(1000*(time.perf_counter()-started)),
                'bestmove':lines[-1].split()[1],'info':info,
                'depth':int(depth[1]) if depth else None,
                'score_type':score[1] if score else None,'score':int(score[2]) if score else None}
        board=chess.Board(initial)
        for move in moves: board.push_uci(move)
        assert result['bestmove']=='0000' and board.is_game_over() or chess.Move.from_uci(result['bestmove']) in board.legal_moves
        return result
    finally: engine.close()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',required=True)
    parser.add_argument('--cases',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    cases=json.loads(args.cases.read_text())
    jars={'original':Path('artifacts/2026-10-29/baseline/titan-baseline.jar'),
          'corrected':Path('artifacts/2026-10-30/terminal-candidate/titan-terminal.jar')}
    result={'method':'Own-engine diagnostic, not objective ground truth. Preserve initial EPD FEN and full move history. Fresh warmed process per sample. Depth-14 comparisons have a 15s ceiling; compare depths before interpreting. Forced moves use equal 5s child budgets and negate side-to-move scores.',
            'sha256':{k:hashlib.sha256(v.read_bytes()).hexdigest() for k,v in jars.items()},'cases':cases}
    for case in cases:
        root=case['before_drop']; initial=case['initial_fen']; moves=root['moves']
        case['root_probes']=[]
        for name,jar in jars.items():
            for command in ['go depth 14 movetime 15000','go movetime 3000']:
                row=probe(args.java,jar,initial,moves,command); row['build']=name
                case['root_probes'].append(row)
                print(json.dumps({'game':case['game'],'probe':name,'command':command,'depth':row['depth'],'move':row['bestmove']}),flush=True)
        row=probe(args.java,jars['corrected'],initial,moves,'go movetime 10000'); row['build']='corrected'
        case['root_probes'].append(row)
        alternatives=sorted({root['played']}|{p['bestmove'] for p in case['root_probes']})
        # Additional candidates selected by inspection of these particular loss
        # fixtures: knight recapture/activation, and pawn rather than rook recapture.
        extras={11:['f3d4','c3b5'],14:['c7d6']}.get(case['game'],[])
        board=chess.Board(root['fen'])
        extras=[move for move in extras if chess.Move.from_uci(move) in board.legal_moves]
        alternatives=sorted(set(alternatives)|set(extras))
        case['additional_candidates_note']='Manual candidates for the selected fixtures, not claimed optimal.'
        case['forced_move_probes']=[]
        for move in alternatives:
            row=probe(args.java,jars['corrected'],initial,moves+[move],'go movetime 5000')
            row['forced_root_move']=move
            row['root_perspective_score']=-row['score'] if row['score'] is not None else None
            case['forced_move_probes'].append(row)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(result,indent=2))
        print(json.dumps({'game':case['game'],'forced_moves':case['forced_move_probes']}),flush=True)


if __name__=='__main__': main()

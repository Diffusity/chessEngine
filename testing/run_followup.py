"""Sequential guarded replacement pairs and direct candidate/baseline match."""
import argparse
import json
import subprocess
import sys
from pathlib import Path
from analyse_match import analyse


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parent.parent
    output=args.output.resolve()
    output.mkdir(parents=True,exist_ok=False)
    original=root/'artifacts/2026-10-29/baseline/titan-baseline.jar'
    reliability=root/'artifacts/2026-10-29/reliability-candidate/titan-reliability.jar'
    tactical=root/'artifacts/2026-10-30/tactical-candidate/titan-tactical.jar'
    terminal=root/'artifacts/2026-10-30/terminal-candidate/titan-terminal.jar'
    jobs=[('replacement-baseline',reliability,original,1,None),
          ('replacement-tactical',tactical,reliability,3,None),
          ('direct-final-vs-original',terminal,original,10,root/'testing/positions/diagnostic-openings.epd')]
    status={'phase':'starting','completed':False,'jobs':[]}
    def save(): (output/'status.json').write_text(json.dumps(status,indent=2))
    save()
    try:
        for name,candidate,baseline,pairs,openings in jobs:
            status['phase']=name; save()
            command=[sys.executable,str(root/'testing/run_match.py'),'--candidate',str(candidate),
                     '--baseline',str(baseline),'--fastchess',str(root/'testing/fastchess-windows-x86-64/fastchess.exe'),
                     '--java',args.java,'--pairs',str(pairs),'--output',str(output/name)]
            if openings: command+=['--openings',str(openings)]
            subprocess.run(command,cwd=root,check=True)
            report=analyse(output/name/'games.pgn')
            (output/name/'summary.json').write_text(json.dumps(report,indent=2))
            status['jobs'].append({'name':name,'report':report}); save()
            if report['completed_games']!=pairs*2 or any(k!='normal' for k in report['terminations']):
                raise RuntimeError('Incomplete games or abnormal termination; investigate before next batch')
        status.update(phase='finished',completed=True); save()
    except BaseException as error:
        status.update(phase='stopped_for_investigation',error=str(error)); save()
        raise


if __name__=='__main__': main()

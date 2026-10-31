"""Predeclared sequential 3+2 timing-policy ablation, without changing defaults."""
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from analyse_match import analyse


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if any(os.environ.get(k) for k in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']):
        parser.error('Inherited JVM options could confound the ablation; review them before running')
    root=Path(__file__).resolve().parent.parent
    output=args.output.resolve(); output.mkdir(parents=True,exist_ok=False)
    candidate=root/'artifacts/2026-10-31/time-policy-candidate/titan-time-policy.jar'
    original=root/'artifacts/2026-10-29/baseline/titan-baseline.jar'
    status={'phase':'starting','completed':False,'jobs':[],
            'plan':'20 full-soft vs default games using the identical JAR; then 20 full-soft vs original games. Same ten openings, paired colours, no early adjudication. No default-policy promotion from a small sample.'}
    def save(): (output/'status.json').write_text(json.dumps(status,indent=2))
    save()
    try:
        for name,baseline in [('full-soft-vs-default',candidate),('full-soft-vs-original',original)]:
            status['phase']=name; save()
            subprocess.run([sys.executable,str(root/'testing/run_match.py'),
                '--java',args.java,'--candidate',str(candidate),'--baseline',str(baseline),
                '--candidate-full-soft-clock','--pairs','10',
                '--fastchess',str(root/'testing/fastchess-windows-x86-64/fastchess.exe'),
                '--openings',str(root/'testing/positions/diagnostic-openings.epd'),
                '--output',str(output/name)],cwd=root,check=True)
            report=analyse(output/name/'games.pgn')
            (output/name/'summary.json').write_text(json.dumps(report,indent=2))
            status['jobs'].append({'name':name,'report':report}); save()
            if report['completed_games']!=20 or any(k!='normal' for k in report['terminations']):
                raise RuntimeError('Incomplete or abnormal match; inspect evidence before continuing')
        status.update(phase='finished',completed=True); save()
    except BaseException as error:
        status.update(phase='stopped_for_investigation',error=str(error)); save()
        raise


if __name__=='__main__': main()

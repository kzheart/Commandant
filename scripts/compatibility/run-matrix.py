#!/usr/bin/env python3
"""Isolated loopback-only Paper smoke tests; requires explicit EULA acceptance."""
import pathlib,subprocess,threading,time,json,sys,shutil,hashlib,argparse
BASE=pathlib.Path(__file__).resolve().parent
MATRIX=[(row['version'], str(row['build']), str(BASE/('java'+str(row['java']))/'bin/java'))
        for row in json.loads((BASE/'matrix.json').read_text())]
parser=argparse.ArgumentParser();parser.add_argument('plugin',nargs='?');parser.add_argument('--versions',help='Comma-separated subset of versions');parser.add_argument('--results-name',default='smoke-results.json');parser.add_argument('--accept-eula',action='store_true',help='Confirm acceptance of https://www.minecraft.net/eula for this isolated test');args=parser.parse_args()
if not args.accept_eula:parser.error('Read https://www.minecraft.net/eula and pass --accept-eula only if you accept it')
plugin=pathlib.Path(args.plugin).resolve() if args.plugin else None
if args.versions:
 selected=set(args.versions.split(','))
 unknown=selected-{row[0] for row in MATRIX}
 if unknown:parser.error('Unknown versions: '+','.join(sorted(unknown)))
 MATRIX=[row for row in MATRIX if row[0] in selected]
results=[]
for index,(version,build,java) in enumerate(MATRIX):
 directory=BASE/('server-'+version);directory.mkdir(exist_ok=True)
 (directory/'eula.txt').write_text('eula=true\n')
 (directory/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+str(25660+index)+'\nonline-mode=true\nenable-rcon=false\nenable-query=false\nview-distance=2\nsimulation-distance=2\nmax-players=2\nspawn-protection=0\nlevel-type=flat\ngenerate-structures=false\nallow-nether=false\n')
 if plugin:
  print('ARTIFACT_SHA256 '+version+' '+hashlib.sha256(plugin.read_bytes()).hexdigest(),flush=True)
  (directory/'plugins').mkdir(exist_ok=True);shutil.copy2(plugin,directory/'plugins'/plugin.name)
  harness=BASE/'CommandantSmoke.jar'
  if harness.exists():shutil.copy2(harness,directory/'plugins'/harness.name)
  fixture=BASE/'NonKetherFixture.jar'
  if version=='26.2' and fixture.exists():shutil.copy2(fixture,directory/'plugins'/fixture.name)
 log=directory/('smoke.log' if plugin else 'baseline.log');ready=threading.Event();lines=[]
 proc=subprocess.Popen([java,'-Xms256M','-Xmx900M','-jar',str(BASE/f'paper-{version}-{build}.jar'),'nogui'],cwd=directory,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 def read():
  with log.open('w') as output:
   for line in proc.stdout:
    output.write(line);output.flush();lines.append(line)
    if 'Done (' in line:ready.set()
 reader=threading.Thread(target=read,daemon=True);reader.start()
 deadline=time.monotonic()+240
 while not ready.is_set() and proc.poll() is None and time.monotonic()<deadline:time.sleep(.5)
 if ready.is_set():
  for command in ['plugins']:
   proc.stdin.write(command+'\n');proc.stdin.flush();time.sleep(1)
  time.sleep(10 if plugin else 2)
 if proc.poll() is None:
  proc.stdin.write('stop\n');proc.stdin.flush()
  try:proc.wait(timeout=45)
  except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
 reader.join(timeout=5)
 result={'artifact_sha256':hashlib.sha256(plugin.read_bytes()).hexdigest() if plugin else None,'version':version,'build':build,'ready':ready.is_set(),'exit':proc.returncode,'log':str(log),'smoke_pass':any('COMMANDANT_SMOKE_PASS' in l for l in lines)};results.append(result)
 print(json.dumps(result),flush=True)
(BASE/(args.results_name if plugin else 'baseline-results.json')).write_text(json.dumps(results,indent=2))

if any(not r['ready'] or r['exit']!=0 or (plugin and not r['smoke_pass']) for r in results):sys.exit(1)

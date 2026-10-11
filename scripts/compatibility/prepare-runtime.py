#!/usr/bin/env python3
"""Fetch official test hosts/JDKs and verify official SHA256 metadata. No server start."""
import concurrent.futures,hashlib,json,pathlib,subprocess,tarfile,zipfile
BASE=pathlib.Path(__file__).resolve().parent
UA='Commandant-Compatibility-Test/1.0 (+https://github.com/kzheart/Commandant)'
def download(url,dest,sha=None):
 if not dest.exists() or (sha and hashlib.sha256(dest.read_bytes()).hexdigest()!=sha.lower()):
  subprocess.run(['curl','--fail','--silent','--show-error','--location','--max-time','180','-A',UA,url,'-o',str(dest)],check=True)
 if sha and hashlib.sha256(dest.read_bytes()).hexdigest()!=sha.lower():raise RuntimeError('SHA256 mismatch: '+str(dest))
 return dest
manifest=[]
matrix=json.loads((BASE/'matrix.json').read_text())
for row in matrix:
 version=row['version']
 metadata=download(f'https://fill.papermc.io/v3/projects/paper/versions/{version}/builds',BASE/f'paper-{version}-builds.json')
 build=next(x for x in json.loads(metadata.read_text()) if x['id']==row['build']);item=build['downloads']['server:default']
 jar=download(item['url'],BASE/item['name'],item['checksums']['sha256']);manifest.append({'name':item['name'],'url':item['url'],'sha256':item['checksums']['sha256']})
 cache=BASE/('server-'+version)/'cache';cache.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(jar) as z:
  if 'patch.properties' in z.namelist():
   p=dict(line.split('=',1) for line in z.read('patch.properties').decode().splitlines() if '=' in line);sha=p['originalHash'];url=p['sourceUrl'].replace('\\:',':');name=f'mojang_{version}.jar'
  elif 'patch.json' in z.namelist():
   p=json.loads(z.read('patch.json'));sha=p['originalHash'];url=p['sourceUrl'];name=f'mojang_{version}.jar'
  else:sha,url,name=z.read('META-INF/download-context').decode().strip().split('\t')
 try:download(url,cache/name,sha)
 except subprocess.CalledProcessError:
  # Historical Mojang S3 URLs can disappear. Resolve the same version through
  # Mojang's current official manifest and still require Paper's exact SHA256.
  index=download('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',BASE/'mojang-version-manifest.json')
  entry=next(x for x in json.loads(index.read_text())['versions'] if x['id']==version)
  details=download(entry['url'],BASE/f'mojang-{version}-metadata.json')
  source=json.loads(details.read_text())['downloads']['server']
  download(source['url'],cache/name,sha)
for major in sorted({row['java'] for row in matrix}):
 metadata=download(f'https://api.adoptium.net/v3/assets/latest/{major}/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse',BASE/f'jdk-{major}-metadata.json')
 item=json.loads(metadata.read_text())[0]['binary']['package'];archive=download(item['link'],BASE/item['name'],item['checksum']);manifest.append({'name':item['name'],'url':item['link'],'sha256':item['checksum']})
 target=BASE/f'java{major}';target.mkdir(exist_ok=True)
 subprocess.run(['tar','-xzf',str(archive),'-C',str(target),'--strip-components=1'],check=True)
(BASE/'artifact-manifest.json').write_text(json.dumps(manifest,indent=2))
print('Verified official downloads ready; inspect manifest and explicitly accept Minecraft EULA before server runs.')

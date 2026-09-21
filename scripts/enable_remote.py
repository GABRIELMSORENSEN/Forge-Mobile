"""Publish the packaged workflow after GitHub grants the workflow scope."""
import base64,json,subprocess
from pathlib import Path
REPO='GABRIELMSORENSEN/Forge-Mobile'
root=Path(__file__).resolve().parents[1]
def gh(args,data=None):
 p=subprocess.run(['gh',*args],input=data,capture_output=True)
 if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace'))
 return p.stdout
info=json.loads(gh(['api',f'repos/{REPO}']))
if not info['private']:raise RuntimeError('O centro precisa permanecer privado')
endpoint=f'repos/{REPO}/contents/.github/workflows/remote.yml'
body={'message':'Enable private remote builds','branch':'main','content':base64.b64encode((root/'app/src/main/assets/remote.yml').read_bytes()).decode()}
p=subprocess.run(['gh','api',endpoint],capture_output=True)
if p.returncode==0:body['sha']=json.loads(p.stdout)['sha']
gh(['api',endpoint,'--method','PUT','--input','-'],json.dumps(body).encode())
gh(['secret','set','FORGE_GITHUB_TOKEN','--repo',REPO],gh(['auth','token']).strip())
print('Fluxo publicado. Envie uma tarefa pelo Forge no celular.')

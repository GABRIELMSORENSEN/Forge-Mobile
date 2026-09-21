"""Seed the user's private hub and optionally a USB-connected debug APK.
No credentials are written to source, command arguments, stdout or shared phone storage.
"""
import argparse,json,os,subprocess,sys
from pathlib import Path

def run(args, data=None):
    p=subprocess.run(args,input=data,capture_output=True)
    if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace'))
    return p.stdout

def main():
    p=argparse.ArgumentParser();p.add_argument('--repo',default='GABRIELMSORENSEN/Forge-Mobile');p.add_argument('--device');p.add_argument('--adb',default=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'));p.add_argument('--codex-account',action='store_true');args=p.parse_args()
    info=json.loads(run(['gh','api',f'repos/{args.repo}']))
    if not info['private'] or not info['permissions']['admin']:raise RuntimeError('Use um repositório privado administrado por sua conta')
    token=run(['gh','auth','token']).strip()
    run(['gh','secret','set','FORGE_GITHUB_TOKEN','--repo',args.repo],token)
    if args.codex_account:
        path=Path.home()/'.codex/auth.json'
        auth=json.loads(path.read_text(encoding='utf-8'))
        if not auth.get('tokens') and not auth.get('OPENAI_API_KEY'):raise RuntimeError('Conta Codex não conectada neste computador')
        run(['gh','secret','set','CODEX_AUTH_JSON','--repo',args.repo],json.dumps(auth).encode())
    bootstrap={'github':token.decode(),'hub':args.repo}
    vercel=Path.home()/'AppData/Roaming/com.vercel.cli/Data/auth.json'
    if vercel.exists():
        value=json.loads(vercel.read_text(encoding='utf-8')).get('token')
        if value:
            run(['gh','secret','set','VERCEL_TOKEN','--repo',args.repo],value.encode());bootstrap['vercel']=value
    supabase=Path.home()/'.supabase/access-token'
    if supabase.exists():
        value=supabase.read_text().strip()
        if value:run(['gh','secret','set','SUPABASE_ACCESS_TOKEN','--repo',args.repo],value.encode());bootstrap['supabase']=value
    if args.device:
        adb=[args.adb,'-s',args.device]
        run(adb+['shell','am','force-stop','com.g4t0xx.forge'])
        run(adb+['shell','run-as','com.g4t0xx.forge','mkdir','-p','files'])
        run(adb+['shell','run-as','com.g4t0xx.forge','sh','-c','"cat > files/bootstrap.json"'],json.dumps(bootstrap).encode())
        run(adb+['shell','am','start','-n','com.g4t0xx.forge/.MainActivity'])
    print('Pareamento concluído. Credenciais enviadas por stdin e armazenadas como segredos privados.')

if __name__=='__main__':main()

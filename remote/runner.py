"""Personal, authenticated GitHub runner. Only dispatched tasks in a private hub."""
from __future__ import annotations
import json, os, platform, re, shutil, subprocess, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.work'
LOG: list[str] = []
SECRETS: list[str] = []

def redact(text: str) -> str:
    for secret in sorted(SECRETS, key=len, reverse=True):
        if len(secret) >= 8:
            text = text.replace(secret, '[REDACTED]')
    return re.sub(r'(?i)(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]+|sk-[A-Za-z0-9_-]{20,})', '[REDACTED]', text)

def run(args, cwd=None, env=None, timeout=2400, input_text=None, stream=False):
    # No shell interpolation of repository names, refs or prompts.
    if not stream:
        p = subprocess.run(args, cwd=cwd, env=env, input=input_text, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
        output = redact(p.stdout + p.stderr)
        LOG.append(output[-60000:])
        if p.returncode:
            raise RuntimeError(f'Comando terminou com código {p.returncode}: {output[-4000:]}')
        return p.stdout
    # Capture output to disk so runaway command output cannot exhaust memory.
    with (WORK / 'process.log').open('w+', encoding='utf-8') as f:
        p = subprocess.Popen(args, cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=f, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        try:
            p.communicate(input=input_text, timeout=timeout)
        except subprocess.TimeoutExpired:
            p.kill(); p.wait()
            raise RuntimeError('Tempo máximo da tarefa excedido')
        f.seek(max(0, f.tell() - 100000))
        output = redact(f.read())
        LOG.append(output[-60000:])
        if p.returncode:
            raise RuntimeError(f'Comando terminou com código {p.returncode}: {output[-5000:]}')
        return output

def validate_target(target: str, owner: str):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', target):
        raise ValueError('Repositório inválido')
    if target.split('/')[0].lower() != owner.lower():
        raise ValueError('Este ambiente pessoal aceita somente repositórios do proprietário')

def mask_auth(auth):
    for v in auth.values():
        if isinstance(v, dict): mask_auth(v)
        elif isinstance(v, str) and len(v) > 15: SECRETS.append(v)

def save_changes(project: Path, branch: str):
    # Refuse common credential files, and do not include binaries in commits.
    paths = run(['git', 'ls-files', '--modified', '--others', '--exclude-standard', '-z'], project).split('\0')
    blocked = []
    for name in paths:
        if not name: continue
        path = project / name
        if path.is_symlink(): blocked.append(name); continue
        if re.search(r'(^|/)(\.env(?:\..*)?|auth\.json|credentials(?:\.json)?|.*\.(?:jks|keystore|pem|key))$', name, re.I):
            if not name.endswith(('.example', '.sample')): blocked.append(name); continue
        if path.is_file():
            if path.stat().st_size > 2 * 1024 * 1024: blocked.append(name); continue
            content = path.read_bytes()
            if any(s.encode() in content for s in SECRETS if len(s) >= 8): blocked.append(name); continue
        run(['git', 'add', '--', name], project)
    # Include deletions as well.
    for name in run(['git', 'ls-files', '--deleted', '-z'], project).split('\0'):
        if name: run(['git', 'add', '--', name], project)
    if run(['git', 'diff', '--cached', '--name-only'], project).strip():
        run(['git', '-c', 'user.name=Forge Mobile', '-c', 'user.email=forge@users.noreply.github.com', 'commit', '-m', f'Forge task {branch}'], project)
        run(['git', 'push', 'origin', f'HEAD:refs/heads/{branch}'], project)
        return True, blocked
    return False, blocked

def collect_builds(project: Path):
    output = WORK / 'output'; output.mkdir(exist_ok=True)
    size = 0; files = []
    for p in project.rglob('*'):
        relative = p.relative_to(project)
        if any(x in relative.parts for x in ('.git', 'node_modules', '.gradle', '.venv', 'venv')): continue
        if p.is_symlink() or not p.is_file() or not p.resolve().is_relative_to(project.resolve()): continue
        if not (p.suffix.lower() in ('.apk', '.exe', '.appimage', '.deb', '.msi', '.aab') or ('artifacts' in relative.parts and p.suffix.lower() in ('.zip', '.gz', '.tar'))): continue
        n=p.stat().st_size
        if size+n > 500*1024*1024: raise RuntimeError('Arquivos gerados excedem 500 MB')
        size+=n
        dest=output/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest);files.append(str(relative))
    return files

def main():
    WORK.mkdir(exist_ok=True); (WORK/'result').mkdir(exist_ok=True)
    result = {'ok': False, 'request_id': os.getenv('FORGE_REQUEST_ID',''), 'platform': platform.system(), 'files': []}
    authfile = None; gh_env = os.environ.copy(); project = WORK/'project'
    for k in ('GH_TOKEN','OPENAI_API_KEY','VERCEL_TOKEN','SUPABASE_ACCESS_TOKEN'):
        if os.getenv(k): SECRETS.append(os.environ[k])
    authraw = os.environ.pop('CODEX_AUTH_JSON', '')
    gh_env.pop('CODEX_AUTH_JSON',None)
    try:
        owner=os.environ['GITHUB_REPOSITORY_OWNER'];target=os.environ['FORGE_TARGET'];mode=os.environ['FORGE_MODE']
        validate_target(target,owner)
        if mode not in ('agent','shell','diagnostic'): raise ValueError('Modo inválido')
        hub=json.loads(run(['gh','api',f'repos/{os.environ["GITHUB_REPOSITORY"]}'],env=gh_env))
        if not hub['private']: raise RuntimeError('Execução exige um repositório privado')
        run(['gh','auth','setup-git'],env=gh_env)
        run(['gh','repo','clone',target,str(project),'--','--quiet'],env=gh_env)
        ref=os.getenv('FORGE_REF','').strip()
        if ref:
            run(['git','check-ref-format','--branch',ref],project)
            run(['git','fetch','origin',ref],project)
            run(['git','checkout','--detach','FETCH_HEAD'],project)
        branch='forge/task-'+os.environ['GITHUB_RUN_ID']
        run(['git','switch','-c',branch],project)
        result['target']=target;result['branch']=branch
        task=os.environ['FORGE_TASK']
        # Secrets are exposed only to the requested trusted personal agent, never job setup hooks.
        env=os.environ.copy();env.pop('CODEX_AUTH_JSON',None);env.pop('OPENAI_API_KEY',None)
        if mode=='diagnostic':
            checks={}
            for tool,args in [('python',[sys.executable,'--version']),('node',['node','--version']),('git',['git','--version']),('java',['java','-version'])]:
                checks[tool]=run(args,project,env).strip()
            result['message']='Ambiente remoto operacional. '+json.dumps(checks,ensure_ascii=False)
        elif mode=='shell':
            shell=['pwsh','-NoProfile','-NonInteractive','-Command',task] if os.name=='nt' else ['bash','-e','-o','pipefail','-c',task]
            result['message']=run(shell,project,env,stream=True)[-40000:]
        else:
            codex=shutil.which('codex')
            if not codex: raise RuntimeError('Codex CLI não instalado')
            codexhome=WORK/'codex-home';codexhome.mkdir(exist_ok=True);env['CODEX_HOME']=str(codexhome)
            key=os.getenv('OPENAI_API_KEY','')
            if key:
                run([codex,'login','--with-api-key'],env=env,input_text=key)
            elif authraw:
                auth=json.loads(authraw);mask_auth(auth)
                authfile=codexhome/'auth.json';authfile.write_text(authraw,encoding='utf-8');authfile.chmod(0o600)
            else: raise RuntimeError('Conecte sua conta Codex ou configure OPENAI_API_KEY em Conexões')
            (codexhome/'config.toml').write_text('cli_auth_credentials_store = "file"\n',encoding='utf-8')
            output=WORK/'answer.txt'
            prompt=('Você está executando uma tarefa pessoal pelo Forge Mobile em um runner temporário. '
                    'Trabalhe no repositório atual, execute e valide o pedido. Responda em português. '
                    'Acesso completo ao runner e à rede autorizado. Não exponha credenciais. '
                    'Não faça push nem commit: o executor salva mudanças em uma branch ao terminar. '
                    'Builds: deixe APK, EXE, AppImage ou DEB dentro do projeto; ZIPs em artifacts/. '
                    'Use GH_TOKEN, VERCEL_TOKEN e SUPABASE_ACCESS_TOKEN apenas quando necessário e disponíveis. '
                    'Não prometa acesso aos arquivos do PC ou controle do telefone. Pedido:\n'+task)
            args=[codex,'exec','--dangerously-bypass-approvals-and-sandbox','--json','-o',str(output)]
            if os.getenv('FORGE_MODEL','').strip():args+=['--model',os.environ['FORGE_MODEL'].strip()]
            args+=['-']
            run(args,project,env,input_text=prompt,stream=True)
            if not output.exists():raise RuntimeError('Codex não retornou resposta final')
            result['message']=redact(output.read_text(encoding='utf-8'))
        changed,blocked=save_changes(project,branch)
        result['committed']=changed;result['excluded_files']=blocked
        result['files']=collect_builds(project)
        result['ok']=True
    except Exception as e:
        result['error']=redact(str(e));print('Tarefa falhou: '+result['error'])
    finally:
        if authfile and authfile.exists():
            try:
                # Persist refreshed auth before deleting the runner's credential file.
                run(['gh','secret','set','CODEX_AUTH_JSON','--repo',os.environ['GITHUB_REPOSITORY']],env=gh_env,input_text=authfile.read_text(encoding='utf-8'))
            except Exception:
                result['auth_warning']='Não foi possível persistir a sessão renovada; reconecte se a próxima tarefa falhar.'
            authfile.unlink(missing_ok=True)
        result['log']=redact('\n'.join(LOG))[-90000:]
        (WORK/'result'/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        print('Resultado salvo. Sucesso:',result['ok'])
    return 0 if result['ok'] else 1

if __name__=='__main__':sys.exit(main())

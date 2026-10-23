"""Private local provisioning. Never writes credentials to GitHub or the APK."""
import argparse,hashlib,json,os,re,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(args,data=None):
 p=subprocess.run(args,input=data,capture_output=True)
 if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')[-1000:])
 return p.stdout
def build_memory():
 notes=[]
 def add(text,source,id=None):notes.append({'id':id or hashlib.sha256((source+text).encode()).hexdigest(),'text':text,'source':source,'updated':int(time.time()*1000)})
 add('O usuário se identifica como G4T0XX / Gabriel. Conta GitHub: GABRIELMSORENSEN. Fala português brasileiro. Usa Windows e Android Samsung A35; trabalha também com Meta Quest, VRChat e ferramentas de criação. Prefere respostas curtas, execução direta e autonomia para escolhas rotineiras.','perfil-importado','perfil-principal')
 add('Prefere que o assistente execute o pedido completo, preserve versões funcionais e valide no aparelho, cena ou produção reais. Build ou HTTP 200 sozinhos não significam conclusão. Explicações longas só quando necessárias ou pedidas. Respeitar escopo literal, como "só isso".','preferencia-importada','preferencia-execucao')
 add('Áreas de trabalho: apps APK nativos, Windows/Electron/Python, apps web React/Vite/Express, GitHub/Vercel/Supabase, edição de vídeos com IA e legendas, Unity/avatares/VRChat/modelagem, áudio/microfone, overlays e tradução VR, streaming OBS e utilitários do sistema. Cada dispositivo tem sua versão independente do Forge, sem limitar o PC pelo celular.','perfil-importado','perfil-areas')
 add('Preservar ALVR, Standable, OVR Advanced Settings e caminhos VR já funcionando; não mudar runtime/áudio/SteamVR sem necessidade expressa. Em Unity/VRChat, salvar backups e preservar estados explicitamente nomeados. O usuário prefere validação real no headset/dispositivo.','preferencia-importada','preferencia-preservar')
 add('Edição: preservar contexto, fala inteligível, frases completas e arcos de preparação/tentativa/resultado. Memes brasileiros variados e contextuais; música discreta e estável por cena, voz em primeiro plano. Legendas fiéis e seletivas, sem frases editoriais inventadas nem emoji genérico. Projetos editáveis em camadas FrameForge, cada revisão em pasta própria; verificar exportação e áudio antes de dizer pronto.','preferencia-importada','preferencia-video')
 add('Identidades de edição distintas: G4T0XX/Neko Rubra (cabelo e orelhas de gato pretos, roupa preta/vermelha); cliente Sharkell (personagem palhaça com cabelo azul/rosa). Não misturar personagens nem copiar a ordem de memes/músicas entre vídeos. Sites devem ter boa hierarquia, responsividade, acessibilidade e movimento intencional.','preferencia-importada','preferencia-identidades')
 registry=Path.home()/'.codex/memories/MEMORY.md'
 if registry.exists():
  content=registry.read_text(encoding='utf-8');sections=re.split(r'(?m)^# Task Group: ',content)
  for section in sections[1:]:
   title=section.splitlines()[0];lines=section.splitlines();facts=[l[2:] for l in lines if l.startswith('- ') and any(w in l.lower() for w in ('the user','user asked','prefer','preserve','requested'))]
   text='Registro anterior de projeto: '+title+'\n'+'\n'.join(facts[:4])
   # No private tokens/keys/raw auth, only project summaries and user preferences.
   if len(text)>16000:text=text[:16000]
   text=re.sub(r'\[Task \d+\]','',text)
   if not re.search(r'(gh[pousr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|BEGIN .*PRIVATE KEY)',text):add(text,'projeto-memoria-anterior')
 roots=[Path('E:/codex projects'),Path.home()/'OneDrive/downloads',Path.home()/'Documents/tradutorvrchat',Path.home()/'OneDrive/downloads/edição de videos',Path.home()/'OneDrive/downloads/avatares']
 inventory=[]
 for root in roots:
  if not root.exists():continue
  for p in root.iterdir():
   if not p.is_dir() or p.name.startswith('.'):continue
   try:entries=[x.name for x in p.iterdir() if x.name not in ('node_modules','.git','.gradle','.venv','build','dist','Library','Temp','Logs')][:30]
   except OSError:continue
   readme=''
   for n in ('README.md','package.json','ProjectSettings/ProjectVersion.txt'):
    f=p/n
    if f.is_file() and f.stat().st_size<150000:
     try:readme=f.read_text(encoding='utf-8',errors='replace')[:1800];break
     except OSError:continue
   # Package manifests/READMEs may contain credential-looking strings. Skip rather than copy them.
   if re.search(r'(gh[pousr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|(?:token|password|secret)\s*[:=]\s*["\'][^"\']{12,})',readme,re.I):readme=''
   item={'path':str(p),'entries':entries,'description':readme};inventory.append(item)
   add('Pasta existente no PC: '+str(p)+'\nArquivos/pastas identificados: '+', '.join(entries)+'\nDescrição disponível: '+readme,'projeto-inventario')
 (ROOT/'.work').mkdir(exist_ok=True)
 (ROOT/'.work/private-project-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2),encoding='utf-8')
 (ROOT/'.work/private-memory.json').write_text(json.dumps(notes,ensure_ascii=False,indent=2),encoding='utf-8')
 return notes
def main():
 p=argparse.ArgumentParser();p.add_argument('--device',default='RQCXC015NYX');p.add_argument('--adb',default=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'));p.add_argument('--with-model',action='store_true');p.add_argument('--with-large-model',action='store_true');args=p.parse_args()
 notes=build_memory();bootstrap={'memory':notes}
 authfile=Path.home()/'.codex/auth.json'
 if authfile.exists():bootstrap['codex_auth']=authfile.read_text(encoding='utf-8')
 key=os.getenv('OPENAI_API_KEY','')
 if key:bootstrap['openai']=key
 pairing=Path.home()/'.forge-assistant/pairing.json'
 if pairing.exists():bootstrap.update(json.loads(pairing.read_text()))
 adb=[args.adb,'-s',args.device]
 run(adb+['shell','am','force-stop','com.g4t0xx.forge']);run(adb+['shell','run-as','com.g4t0xx.forge','mkdir','-p','files/models'])
 run(adb+['shell','run-as','com.g4t0xx.forge','sh','-c','"cat > files/bootstrap.json"'],json.dumps(bootstrap,ensure_ascii=False).encode())
 for name,enabled in [('Qwen3.5-0.8B-Q4_K_M.gguf',args.with_model),('Qwen3.5-4B-Q4_K_M.gguf',args.with_large_model)]:
  if enabled:
   source=Path('E:/codex projects/POLIGON-ASSISTENT-AI/models')/name
   temp='/data/local/tmp/forge-'+name
   run(adb+['push',str(source),temp]);run(adb+['shell','run-as','com.g4t0xx.forge','cp',temp,'files/models/'+name]);run(adb+['shell','rm',temp])
 run(adb+['shell','am','start','-n','com.g4t0xx.forge/.MainActivity'])
 print('Provisionamento privado concluído:',len(notes),'notas. Nenhuma credencial publicada.')
if __name__=='__main__':main()

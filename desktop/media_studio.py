"""Local Stable Diffusion and Wan, with pinned models and saved jobs."""
import concurrent.futures,hashlib,json,os,shutil,subprocess,sys,threading,time,urllib.request,uuid
from pathlib import Path
ROOT=Path(getattr(sys,'_MEIPASS',Path(__file__).resolve().parents[1]));APPDIR=Path(sys.executable).parent if getattr(sys,'frozen',False) else ROOT
HOME=Path(os.getenv('FORGE_DATA',str(Path.home()/'.forge-assistant')));MODELS=HOME/'media-models';OUTPUT=HOME/'workspace/media';JOBS=HOME/'media-jobs.json';LOCK=threading.RLock();EXEC=concurrent.futures.ThreadPoolExecutor(max_workers=1);PROCESS=None;STATUS='Pronto';BUSY=False
MODELS.mkdir(parents=True,exist_ok=True);OUTPUT.mkdir(parents=True,exist_ok=True)
def manifest():return json.loads((ROOT/'app/src/main/assets/media-models.json').read_text(encoding='utf-8'))
def load():
 with LOCK:return json.loads(JOBS.read_text(encoding='utf-8')) if JOBS.exists() else []
def update(j):
 with LOCK:
  items=[x for x in load() if x['id']!=j['id']];items.insert(0,j);tmp=JOBS.with_suffix('.new');tmp.write_text(json.dumps(items,ensure_ascii=False),encoding='utf-8');os.replace(tmp,JOBS)
def binary():return next((str(p) for p in [APPDIR/'media-runtime/sd-cli.exe',HOME/'media-runtime/sd-cli.exe',ROOT/'.work/sd-windows/sd-cli.exe'] if p.is_file()),shutil.which('sd-cli'))
def info():
 specs=manifest()
 for m in specs:m['installed']=(MODELS/m['file']).exists() and (MODELS/m['file']).stat().st_size==m['size']
 return {'models':specs,'jobs':load(),'busy':BUSY,'status':STATUS,'runtime':bool(binary())}
def download(m):
 global STATUS
 dest=MODELS/m['file']
 if dest.exists() and dest.stat().st_size==m['size']:return
 if shutil.disk_usage(MODELS).free<m['size']+300000000:raise RuntimeError('Armazenamento insuficiente')
 tmp=dest.with_suffix(dest.suffix+'.part')
 def progress(n,size,total):
  global STATUS
  STATUS=m['file']+': '+str(min(100,n*size*100//m['size']))+'%'
 urllib.request.urlretrieve(m['url'],tmp,progress);h=hashlib.sha256()
 with tmp.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 if tmp.stat().st_size!=m['size'] or h.hexdigest()!=m['sha256']:raise RuntimeError('Verificação do modelo falhou')
 os.replace(tmp,dest)
def install(kind):
 global BUSY
 if BUSY:raise RuntimeError('Outra geração ou instalação está ativa')
 if kind not in ('image','video'):raise ValueError('Tipo inválido')
 BUSY=True
 def work():
  global BUSY,STATUS
  try:
   for m in manifest():
    if m['kind']==kind:download(m)
   STATUS='Modelos instalados e verificados'
  except Exception as e:STATUS='Instalação falhou: '+str(e)
  finally:BUSY=False
 EXEC.submit(work);return {'started':True}
def submit(a):
 global BUSY
 with LOCK:
  if BUSY:raise RuntimeError('Aguarde a geração ou instalação atual')
  if a.get('mode')=='image_motion':raise ValueError('Modo leve usa o codificador Android. Neste PC selecione Wan.')
  if a.get('kind','image') not in ('image','video') or not a.get('prompt','').strip():raise ValueError('Descreva a imagem ou vídeo')
  j={**a,'id':str(uuid.uuid4()),'kind':a.get('kind','image'),'status':'queued','created':int(time.time()*1000)};update(j);BUSY=True;EXEC.submit(generate,j);return {'id':j['id']}
def generate(j):
 global BUSY,STATUS,PROCESS
 try:
  j['status']='running';update(j);exe=binary();kind=j['kind']
  if not exe:raise RuntimeError('Motor sd-cli não instalado')
  for m in manifest():
   if m['kind']==kind and not ((MODELS/m['file']).exists() and (MODELS/m['file']).stat().st_size==m['size']):raise RuntimeError('Instale os modelos de '+kind+' antes de gerar')
  w,h,steps=int(j.get('width',512)),int(j.get('height',512)),int(j.get('steps',2 if kind=='image' else 8))
  if not(64<=w<=4096 and 64<=h<=4096 and w%64==0 and h%64==0 and 1<=steps<=100):raise ValueError('Resolução múltipla de 64; etapas entre 1 e 100')
  file=j['id']+('.png' if kind=='image' else '.webm');dest=OUTPUT/file
  args=[exe,'-p',j['prompt'],'-W',str(w),'-H',str(h),'--steps',str(steps),'--threads',str(j.get('threads',6)),'--seed',str(j.get('seed',42)),'--sampling-method','euler','--vae-tiling','-o',str(dest)]
  if j.get('disk',False):args+=['--params-backend','disk','--mmap','--disable-prefetch']
  if kind=='image':args+=['-m',str(MODELS/'sd_turbo-f16-q8_0.gguf'),'--cfg-scale','1']
  else:
   frames=int(j.get('frames',17))
   if frames<5 or frames>257 or (frames-1)%4:raise ValueError('Quadros devem ser 4n+1, entre 5 e 257')
   args+=['-M','vid_gen','--diffusion-model',str(MODELS/'Wan2.1-T2V-1.3B-Q4_K_M.gguf'),'--t5xxl',str(MODELS/'umt5-xxl-encoder-Q3_K_M.gguf'),'--vae',str(MODELS/'wan_2.1_vae.safetensors'),'--video-frames',str(frames),'--fps',str(j.get('fps',16)),'--cfg-scale','6','--flow-shift','3']
  STATUS='Gerando '+kind+' neste PC'
  with (HOME/('media-'+j['id']+'.log')).open('w',encoding='utf-8') as log:
   PROCESS=subprocess.Popen(args,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
   try:code=PROCESS.wait(timeout=3000)
   except subprocess.TimeoutExpired:PROCESS.kill();raise RuntimeError('Tempo excedeu 50 minutos; parâmetros salvos')
  if code or not dest.exists() or dest.stat().st_size<100:raise RuntimeError('Geração falhou, código '+str(code)+'. Consulte o log e ajuste resolução/modelo.')
  j.update(status='done',file=file,bytes=dest.stat().st_size,url='/media/'+file);update(j);STATUS='Geração concluída'
 except Exception as e:j.update(status='failed',error=str(e));update(j);STATUS=str(e)
 finally:BUSY=False;PROCESS=None
def stop():
 if PROCESS and PROCESS.poll() is None:PROCESS.terminate()
 return {'stopping':True}

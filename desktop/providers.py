"""Direct API calls with credentials held on this PC only."""
import json,os,subprocess,urllib.request,urllib.error
from pathlib import Path

def call(a,config):
 if config.get('offline'):raise RuntimeError('Modo offline: API remota desativada')
 provider=a['provider'];host={'github':'api.github.com','vercel':'api.vercel.com','supabase':'api.supabase.com'}[provider]
 path=a['path'];method=a.get('method','GET').upper()
 if not path.startswith('/') or path.startswith('//') or any(c in path for c in ('\\','#','\r','\n')):raise ValueError('Caminho invalido')
 if '/secrets' in path.lower():raise ValueError('Publicacao de credenciais desativada')
 if method not in ('GET','POST','PUT','PATCH','DELETE'):raise ValueError('Metodo invalido')
 token=config.get(provider,'')
 if not token and provider=='github':
  result=subprocess.run(['gh','auth','token'],capture_output=True,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
  if result.returncode==0:token=result.stdout.strip()
 if not token and provider=='vercel':
  file=Path.home()/'AppData/Roaming/com.vercel.cli/Data/auth.json'
  if file.exists():token=json.loads(file.read_text()).get('token','')
 if not token and provider=='supabase':
  file=Path.home()/'.supabase/access-token'
  if file.exists():token=file.read_text().strip()
 if not token:raise RuntimeError('Conecte a conta '+provider+' neste PC')
 class NoRedirect(urllib.request.HTTPRedirectHandler):
  def redirect_request(self,*args):return None
 body=json.dumps(a['body']).encode() if 'body' in a else None
 request=urllib.request.Request('https://'+host+path,body,{'Authorization':'Bearer '+token,'User-Agent':'Forge-Personal','Content-Type':'application/json','Accept':'application/json'},method=method)
 try:response=urllib.request.build_opener(NoRedirect).open(request,timeout=60)
 except urllib.error.HTTPError as e:response=e
 with response:return {'status':response.code,'body':response.read(1000000).decode('utf-8',errors='replace').replace(token,'[credencial]')}

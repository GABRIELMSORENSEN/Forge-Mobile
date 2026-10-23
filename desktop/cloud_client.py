"""Small direct Responses client; avoids loading CLI plugins for every conversation turn."""
import json,urllib.request,urllib.error
from pathlib import Path
class CloudClient:
 def __init__(self):self.low=False;self.quota='Não informada pelo provedor';self.usage={}
 def generate(self,model,system,prompt,api_key=''):
  chat=not api_key
  if chat:
   auth=json.loads((Path.home()/'.codex/auth.json').read_text(encoding='utf-8'));tokens=auth['tokens'];token=tokens['access_token'];url='https://chatgpt.com/backend-api/codex/responses'
  else:token=api_key;url='https://api.openai.com/v1/responses'
  headers={'Authorization':'Bearer '+token,'Content-Type':'application/json','Accept':'text/event-stream','User-Agent':'codex_cli_rs/0.155.1 (Forge personal desktop)','originator':'codex_cli_rs'}
  if chat:headers['ChatGPT-Account-ID']=tokens.get('account_id','')
  body={'model':model,'instructions':system,'input':[{'role':'user','content':[{'type':'input_text','text':prompt}]}],'tools':[],'tool_choice':'auto','parallel_tool_calls':False,'store':False,'stream':True,'include':[]}
  req=urllib.request.Request(url,json.dumps(body).encode(),headers)
  try:
   with urllib.request.urlopen(req,timeout=180) as r:
    used=max([float(r.headers.get(h,'-1')) for h in ('x-codex-primary-used-percent','x-codex-secondary-used-percent')])
    if used>=0:self.quota=f'Maior janela usada: {used}%';self.low=used>=90
    answer=[];done=False
    for line in r:
     line=line.decode('utf-8').strip()
     if not line.startswith('data: ') or line=='data: [DONE]':continue
     event=json.loads(line[6:]);kind=event.get('type')
     if kind=='response.output_text.delta':answer.append(event.get('delta',''))
     if kind=='response.completed':done=True;self.usage=event.get('response',{}).get('usage',{})
     if kind in ('error','response.failed'):raise RuntimeError('Provedor interrompeu a resposta')
    if not done or not answer:raise RuntimeError('Resposta online incompleta')
    return ''.join(answer)
  except urllib.error.HTTPError as e:
   if e.code in (402,429):self.low=True
   raise RuntimeError('Codex HTTP '+str(e.code)) from None

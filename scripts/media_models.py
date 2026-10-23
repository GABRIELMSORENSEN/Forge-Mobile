"""Resolve pinned public model files and optionally download them with hash checks."""
import argparse,hashlib,json,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SPECS=[('image','Green-Sky/SD-Turbo-GGUF','sd_turbo-f16-q8_0.gguf'),('video','samuelchristlie/Wan2.1-T2V-1.3B-GGUF','Wan2.1-T2V-1.3B-Q4_K_M.gguf'),('video','city96/umt5-xxl-encoder-gguf','umt5-xxl-encoder-Q3_K_M.gguf'),('video','Comfy-Org/Wan_2.1_ComfyUI_repackaged','split_files/vae/wan_2.1_vae.safetensors')]
def manifest():
 result=[]
 for kind,repo,name in SPECS:
  d=json.load(urllib.request.urlopen('https://huggingface.co/api/models/'+repo+'?blobs=true'));f=next(x for x in d['siblings'] if x['rfilename']==name)
  result.append({'kind':kind,'file':Path(name).name,'size':f['size'],'sha256':f['lfs']['sha256'],'url':'https://huggingface.co/'+repo+'/resolve/'+d['sha']+'/'+name})
 (ROOT/'app/src/main/assets/media-models.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');return result
def download(spec,dest):
 dest=Path(dest);dest.mkdir(parents=True,exist_ok=True);p=dest/spec['file']
 if p.exists() and p.stat().st_size==spec['size']:return p
 temp=p.with_suffix(p.suffix+'.part');urllib.request.urlretrieve(spec['url'],temp)
 digest=hashlib.sha256()
 with temp.open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):digest.update(block)
 if digest.hexdigest()!=spec['sha256']:raise RuntimeError('SHA-256 incorreto: '+p.name)
 temp.replace(p);return p
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--download',choices=['image','video','all']);a=p.parse_args();m=manifest()
 for spec in m:
  if a.download in (spec['kind'],'all'):print(download(spec,Path.home()/'.forge-assistant/media-models'),flush=True)

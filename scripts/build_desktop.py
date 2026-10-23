import subprocess,sys,shutil,os
from pathlib import Path
root=Path(__file__).resolve().parents[1]
args=[sys.executable,'-m','PyInstaller','--noconfirm','--clean','--onedir','--windowed','--name','Forge-PC','--distpath',str(root/'artifacts/windows'),'--workpath',str(root/'.work/desktop-build'),'--specpath',str(root/'.work'),'--add-data',str(root/'app/src/main/assets')+os.pathsep+'app/src/main/assets','--add-data',str(root/'desktop/voice_capture.py')+os.pathsep+'desktop','--hidden-import','media_studio','--paths',str(root/'desktop'),'--exclude-module','torch','--exclude-module','tensorflow','--exclude-module','faster_whisper',str(root/'desktop/forge_pc.py')]
subprocess.run(args,cwd=root,check=True)
dest=root/'artifacts/windows/Forge-PC/runtime';source=Path('E:/codex projects/POLIGON-ASSISTENT-AI/runtime/llama-windows')
if source.exists():shutil.copytree(source,dest,dirs_exist_ok=True)
media=root/'.work/sd-windows'
if media.exists():shutil.copytree(media,dest.parent/'media-runtime',dirs_exist_ok=True)
cloud=Path('E:/codex projects/POLIGON-ASSISTENT-AI/runtime/cloudflared.exe')
if cloud.exists():shutil.copy2(cloud,dest.parent/'runtime/cloudflared.exe')
shutil.copy2(root/'app/src/main/assets/llama-LICENSE.txt',dest.parent/'llama-LICENSE.txt')
for name in ('LICENSE','NOTICE'):
    source=root/'.work/stable-diffusion.cpp'/name
    if source.exists():shutil.copy2(source,dest.parent/('stable-diffusion-'+name+'.txt'))
print('Desktop application:',dest.parent/'Forge-PC.exe')

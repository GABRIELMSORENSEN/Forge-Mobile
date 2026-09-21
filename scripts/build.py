"""Build signed APKs; signing material never enters the repository."""
import json, os, secrets, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
private=Path.home()/'.forge-mobile'
private.mkdir(exist_ok=True)
config=private/'signing.json'
if not config.exists():
    env={'FORGE_KEYSTORE':str(private/'release.jks'),'FORGE_STORE_PASSWORD':secrets.token_urlsafe(36)}
    signing_env=os.environ.copy();signing_env.update(env)
    subprocess.run(['keytool','-genkeypair','-keystore',env['FORGE_KEYSTORE'],'-storepass:env','FORGE_STORE_PASSWORD','-keypass:env','FORGE_STORE_PASSWORD','-alias','forge','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Forge Mobile, OU=Personal Development, O=G4T0XX, C=BR'],env=signing_env,check=True,capture_output=True)
    config.write_text(json.dumps(env),encoding='utf-8')
env=os.environ.copy();env.update(json.loads(config.read_text(encoding='utf-8')))
subprocess.run([str(ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')),'assembleDebug','assembleRelease','lintDebug','--console','plain'],cwd=ROOT,env=env,check=True)

"""Exercise the running debug app's native API on a USB-connected device via CDP."""
import json,os,sys
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
report={}
with sync_playwright() as p:
 b=p.chromium.connect_over_cdp('http://127.0.0.1:'+os.getenv('FORGE_CDP_PORT','9227'));page=b.contexts[0].pages[0]
 page.evaluate('window.appReady')
 if '--remove-invalid-vercel' in sys.argv:page.evaluate("rpc('save',{vercel:''})")
 report['config']=page.evaluate("rpc('config')")
 report['github_account']=page.evaluate("rpc('account').then(r=>r.login)")
 assert report['github_account']=='GABRIELMSORENSEN'
 page.evaluate("navigate('projects')")
 report['repos_rendered']=page.locator('[data-repo]').count()
 assert report['repos_rendered']>0
 report['gatarme_present']=page.locator('[data-repo="GABRIELMSORENSEN/GATARME"]').count()==1
 assert report['gatarme_present']
 report['vercel']=page.evaluate("rpc('vercel').then(r=>({ok:true,projects:r.projects.length})).catch(e=>({ok:false,error:e.message}))")
 report['secrets']=page.evaluate("rpc('secrets').then(r=>r.secrets.map(s=>s.name))")
 report['encrypted_box']=page.evaluate("async()=>{await sodium.ready;let pair=sodium.crypto_box_keypair();let c=sodium.crypto_box_seal(sodium.from_string('forge-test'),pair.publicKey);return sodium.to_string(sodium.crypto_box_seal_open(c,pair.publicKey,pair.privateKey))==='forge-test';}")
 assert report['encrypted_box']
 report['invalid_operation_rejected']=page.evaluate("rpc('arbitrary').then(()=>false).catch(()=>true)")
 assert report['invalid_operation_rejected']
 report['invalid_link_rejected']=page.evaluate("rpc('open',{url:'https://example.com/steal'}).then(()=>false).catch(()=>true)")
 assert report['invalid_link_rejected']
 report['workflows']=page.evaluate("rpc('runs').then(r=>({ok:true,count:r.total_count})).catch(e=>({ok:false,error:e.message}))")
 page.evaluate("navigate('settings')")
 assert page.locator('#codexStatus').inner_text()=='CONFIGURADO'
 page.evaluate("state.target='GABRIELMSORENSEN/Forge-Mobile';persist();navigate('studio')")
 report['screen_metrics']=page.evaluate("({width:innerWidth,height:innerHeight,hidden:document.hidden})")
 try:page.screenshot(path=str(root/'.work'/'ui-check.png'))
 except Exception:pass
 b.close()
(root/'docs'/('device-check'+('-emulator' if os.getenv('FORGE_CDP_PORT') else '')+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))

import importlib.util,json,os,tempfile,unittest
from pathlib import Path
class DesktopTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();os.environ['FORGE_DATA']=self.temp.name
  spec=importlib.util.spec_from_file_location('forge_test',Path(__file__).resolve().parents[1]/'desktop/forge_pc.py');self.m=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.m)
 def tearDown(self):self.m.DB.close();self.m.EXEC.shutdown(wait=False);self.temp.cleanup();os.environ.pop('FORGE_DATA',None)
 def test_memory_merge_is_idempotent_and_keeps_newer(self):
  m=self.m;m.receive_memory({'memory':[{'id':'m','text':'new','source':'preferencia','updated':200}]});m.receive_memory({'memory':[{'id':'m','text':'old','source':'preferencia','updated':100}]});self.assertEqual(m.rows('SELECT text FROM memory')[0]['text'],'new')
  item={'thread':'t','role':'user','text':'lembre','created':'123'};m.receive_memory({'messages':[item]});m.receive_memory({'messages':[item]});self.assertEqual(len(m.rows('SELECT * FROM messages')),1)
 def test_profile_has_priority_in_context(self):
  m=self.m
  for i in range(50):m.write('INSERT INTO memory VALUES(?,?,?,?)',str(i),'GitHub project '+'x'*300,'projeto',200)
  m.write('INSERT INTO memory VALUES(?,?,?,?)','perfil-principal','GitHub: GABRIELMSORENSEN','perfil-importado',100)
  text=m.context({'thread':'t','prompt':'GitHub','completed':[]})
  self.assertIn('GABRIELMSORENSEN',text[:200])
 def test_file_updates_require_current_hash(self):
  m=self.m;m.C['access']='full';a={'action':'write_file','path':'note.txt','content':'abc','expected_sha256':'missing'};result=m.tool(a);self.assertTrue(m.tool(a)['reused'])
  with self.assertRaises(RuntimeError):m.tool({**a,'content':'stale'})
  self.assertEqual(m.tool({**a,'content':'new','expected_sha256':result['sha256']})['sha256'],m.filehash(m.WORK/'note.txt'))
 def test_restricted_access_denies_outside_workspace(self):
  with self.assertRaises(RuntimeError):self.m.resolve(str(Path(self.temp.name).parent/'outside.txt'))
  with self.assertRaises(RuntimeError):self.m.tool({'action':'terminal','command':'echo unauthorized'})
 def test_offline_blocks_web(self):
  self.m.C['offline']=True
  with self.assertRaises(RuntimeError):self.m.tool({'action':'web_read','url':'https://example.com'})
 def test_three_agents_share_results_and_write_once(self):
  m=self.m;m.C['access']='full';calls=[]
  def local(system,prompt):
   calls.append('planner' if 'Planeje sem ações' in system else 'reviewer')
   return json.dumps({'plan':'Criar e validar arquivo','handoff':'Estado preservado'} if 'Planeje sem ações' in system else {'action':'final','text':'Arquivo confirmado','handoff':'Concluído'})
  replies=iter([{'action':'write_file','path':'proof.txt','content':'OK','expected_sha256':'missing','handoff':'Escrever uma vez'},{'action':'final','text':'Arquivo confirmado','handoff':'Concluído'}]);m.local_generate=local;m.generate=lambda *args:json.dumps(next(replies))
  j={'id':'job','thread':'thread','prompt':'Crie arquivo de prova','model':'local','team':True,'status':'queued','events':[],'completed':[],'steps':0,'handoff':''};m.run(j)
  self.assertEqual(j['status'],'done');self.assertEqual(calls,['planner','reviewer']);self.assertEqual((m.WORK/'proof.txt').read_text(),'OK');self.assertEqual(len(m.rows('SELECT * FROM actions')),1)
 def test_uncertain_action_is_not_replayed_after_restart(self):
  m=self.m;m.C['access']='full';a={'action':'write_file','path':'never.txt','content':'x','expected_sha256':'missing'}
  import hashlib
  ident=hashlib.sha256(('job0'+json.dumps(a,sort_keys=True)).encode()).hexdigest();m.write('INSERT INTO actions VALUES(?,?)',ident,json.dumps({'status':'started'}))
  j={'id':'job','thread':'t','prompt':'Continue','model':'local','team':False,'status':'queued','events':[],'completed':[],'steps':0,'pending_action':a,'handoff':'Ação iniciada'};m.run(j)
  self.assertEqual(j['status'],'failed');self.assertFalse((m.WORK/'never.txt').exists());self.assertIn('incerto',j['error'])
if __name__=='__main__':unittest.main()

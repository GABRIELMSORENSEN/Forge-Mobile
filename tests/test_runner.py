import importlib.util,os,subprocess,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('runner',Path(__file__).resolve().parents[1]/'remote/runner.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)

class RunnerTests(unittest.TestCase):
 def test_target_rejects_shell_injection_and_other_owners(self):
  for name in ['foo/bar;touch /tmp/x','foo/../../x','other/project','foo/proj\ncmd']:
   with self.assertRaises(ValueError):r.validate_target(name,'foo')
  r.validate_target('Foo/my-project','foo')
 def test_redacts_actual_and_token_patterns(self):
  r.SECRETS[:]=['private-value-example']
  self.assertEqual(r.redact('secret private-value-example'),'secret [REDACTED]')
  self.assertNotIn('ghp_',r.redact('ghp_'+'x'*30))
 def test_never_collects_source_or_credentials(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);r.WORK=root/'work';r.WORK.mkdir();project=root/'project';project.mkdir()
   (project/'auth.json').write_text('private');(project/'main.py').write_text('print(1)')
   (project/'app.apk').write_bytes(b'APK');(project/'node_modules').mkdir();(project/'node_modules'/'evil.exe').write_bytes(b'bad')
   files=r.collect_builds(project)
   self.assertEqual(files,['app.apk']);self.assertEqual((r.WORK/'output/app.apk').read_bytes(),b'APK')
 def test_credentials_not_staged(self):
  with tempfile.TemporaryDirectory() as d:
   path=Path(d);subprocess.run(['git','init',str(path)],capture_output=True,check=True)
   (path/'.env').write_text('SECRET=abc');(path/'auth.json').write_text('private');(path/'leak.txt').write_text('private-value-example')
   r.SECRETS[:]=['private-value-example']
   changed,blocked=r.save_changes(path,'forge/test')
   self.assertFalse(changed);self.assertEqual(set(blocked),{'.env','auth.json','leak.txt'})
   self.assertEqual(subprocess.run(['git','diff','--cached','--name-only'],cwd=path,capture_output=True,text=True).stdout,'')

if __name__=='__main__':unittest.main()

"""No network, GUI, builds or actual contacts in hosted exchange validation tests."""
import base64,copy,importlib.util,json,sys,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('hosted_exchange',ROOT/'tooling/hosted_exchange.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
class HostedExchangeTest(unittest.TestCase):
    def values(self):
        network=(ROOT/'shared/fixtures/network-v1.json').read_bytes();report=(ROOT/'shared/fixtures/field-report-v1.json').read_bytes()
        n=json.loads(network);n['entities'].append(copy.deepcopy(n['entities'][0]));n['entities'][1]['id']='synthetic-second';network=json.dumps(n).encode()
        receipt={'synthetic_only':True,'limitations':'Synthetic fixtures only; no personal contacts or physical device.','actual_saf_open_and_create_document':True,'status':'PASS','selected_export_count':2,'report_import_count':1,'package':'com.fieldconsole.debug','installed_apk_sha256':'a'*64,'source_sha':'b'*40,'files':[{'name':'device-'+k+'.json','bytes':len(v),'sha256':h.sha(v)} for k,v in [('network',network),('report',report)]]}
        return {k:(raw,h.document(raw)) for k,raw in [('network',network),('report',report),('android_receipt',json.dumps(receipt).encode())]}
    def test_bad_base64(self):
        with self.assertRaises(h.Rejected):h.decode('@@bad')
    def test_oversized_decoded(self):
        with self.assertRaises(h.Rejected):h.decode(base64.b64encode(b' '*32769).decode())
    def test_nonfinite_duplicate_and_depth(self):
        for raw in (b'{"x":NaN}',b'{"x":1,"x":2}',b'['*34+b'0'+b']'*34):
            with self.assertRaises(h.Rejected):h.document(raw)
    def test_version_and_hash_failures(self):
        v=self.values();self.assertEqual(h.validate_inputs(v,'a'*64),'b'*40)
        with self.assertRaises(h.Rejected):h.validate_inputs(v,'c'*64)
        v['network'][1]['schema_version']=999
        with self.assertRaises(ValueError):h.validate_inputs(v,'a'*64)
    def test_export_attestation_required(self):
        for field,value in [('synthetic_only',False),('actual_saf_open_and_create_document',False),('source_sha','future'),('selected_export_count',3)]:
            v=self.values();v['android_receipt'][1][field]=value
            with self.assertRaises(h.Rejected):h.validate_inputs(v,'a'*64)
        v=self.values();v['android_receipt'][1]['files'][0]['sha256']='f'*64
        with self.assertRaises(h.Rejected):h.validate_inputs(v,'a'*64)
    def test_dispatch_is_data(self):
        event={'inputs':{'release_tag':'v4.0.0-beta.6; echo bad','candidate_run_id':'123'}}
        with self.assertRaises(h.Rejected):h.inputs(event)
    def asset_fixture(self,root):
        source='b'*40;version='4.0.0-beta.6';prefix='FieldIntelligenceConsole-'+version
        names=['FieldIntelligenceConsole-BETA-Android.apk','FieldIntelligenceConsole-BETA-Windows-x64.exe','field_intelligence_console-4.0.0b6-py3-none-any.whl','field_intelligence_console-4.0.0b6.tar.gz',prefix+'-source.zip','SBOM.cdx.json','THIRD_PARTY_NOTICES.txt','artifact-inventory.json','RELEASE_NOTES.md']
        for name in names:(root/name).write_bytes(b'synthetic-not-executable')
        with zipfile.ZipFile(root/names[4],'w') as z:z.comment=source.encode();z.writestr('README.md','synthetic')
        records=[{'name':n,'bytes':(root/n).stat().st_size,'sha256':h.sha((root/n).read_bytes())} for n in names]
        pin=json.loads((ROOT/'shared/release-signing.json').read_text(encoding='utf-8'))['android_testing_certificate_sha256']
        android={'status':'PASS','source_sha':source,'source_dirty':False,'unit':[{'variant':v,'status':'PASS','tests':97,'failures':0,'errors':0,'skipped':0} for v in ('Debug','Release')],'lint':[{'status':'PASS','errors':0}]*2,'runtime_validation':{'status':'PASS','tests':6},'artifacts':[{**records[0],'variant':v,'certificate_sha256':pin,'signature_verified':True,'package':'com.fieldconsole.debug' if v=='debug' else 'com.fieldconsole','version':version} for v in ('debug','release')]}
        windows={'status':'PASS','source_sha':source,'worktree_dirty':False,'focused_tests':14,'packaged_widget_checks':28,'native_checks':7,'binary_metadata':{**records[1],'machine':'AMD64','subsystem':'Windows GUI','product_version':version}}
        python={'status':'PASS','source_sha':source,'source_dirty':False,'unit_tests':47,'unit_failures':0,'unit_skipped':0,'isolated_wheel':'passed','artifacts':copy.deepcopy(records[2:4])}
        m={'source_sha':source,'tag':'v'+version,'version':version,'artifacts':records,'android':android,'windows':windows,'python':python}
        self.write_manifest(root,m);return m
    def write_manifest(self,root,m):
        (root/'release-manifest.json').write_text(json.dumps(m),encoding='utf-8')
        (root/'SHA256SUMS.txt').write_text(''.join(h.sha(f.read_bytes())+'  '+f.name+'\n' for f in root.iterdir() if f.name!='SHA256SUMS.txt'),encoding='utf-8')
    def test_all_asset_hashes_before_execution(self):
        with tempfile.TemporaryDirectory() as owned:
            root=Path(owned);m=self.asset_fixture(root);h.checked_assets(root,m['source_sha'],m['tag'])
            with self.assertRaises(h.Rejected):h.checked_assets(root,'d'*40,m['tag'])
            next(root.glob('*.exe')).write_bytes(b'tampered')
            with self.assertRaises(h.Rejected):h.checked_assets(root,m['source_sha'],m['tag'])
    def test_gate_signing_and_receipt_binding(self):
        mutations=[lambda m:m['windows'].update(focused_tests=13),lambda m:m['windows'].update(native_checks=6),lambda m:m['windows'].update(packaged_widget_checks=27),lambda m:m['android']['runtime_validation'].update(tests=5),lambda m:m['android']['artifacts'][0].update(certificate_sha256='f'*64),lambda m:m['windows']['binary_metadata'].update(sha256='f'*64),lambda m:m['python']['artifacts'][0].update(sha256='f'*64)]
        for change in mutations:
            with tempfile.TemporaryDirectory() as owned:
                root=Path(owned);m=self.asset_fixture(root);change(m);self.write_manifest(root,m)
                with self.assertRaises(ValueError):h.checked_assets(root,m['source_sha'],m['tag'])
    def test_allowlist_and_source_archive(self):
        with tempfile.TemporaryDirectory() as owned:
            root=Path(owned);m=self.asset_fixture(root);(root/'unapproved.txt').write_text('synthetic',encoding='utf-8');self.write_manifest(root,m)
            with self.assertRaises(h.Rejected):h.checked_assets(root,m['source_sha'],m['tag'])
        with tempfile.TemporaryDirectory() as owned:
            root=Path(owned);m=self.asset_fixture(root);archive=next(root.glob('*source.zip'))
            with zipfile.ZipFile(archive,'a') as z:z.comment=b'c'*40
            next(x for x in m['artifacts'] if x['name']==archive.name).update(sha256=h.sha(archive.read_bytes()),bytes=archive.stat().st_size);self.write_manifest(root,m)
            with self.assertRaises(h.Rejected):h.checked_assets(root,m['source_sha'],m['tag'])
    def test_remote_source_and_workflow(self):
        source='b'*40;repo={'private':True,'full_name':h.REPOSITORY};run={'status':'completed','conclusion':'success','head_sha':source,'path':'.github/workflows/release.yml','repository':repo,'head_branch':'v4.0.0-beta.6'}
        response=[repo,run,{'object':{'type':'tag','sha':'c'*40}},{'object':{'type':'commit','sha':source}}]
        with patch.object(h,'api',side_effect=response):h.verify_remote('v4.0.0-beta.6','123',source)
        for key,value in [('head_sha','d'*40),('path','untrusted.yml'),('conclusion','failure')]:
            bad={**run,key:value}
            with patch.object(h,'api',side_effect=[repo,bad]),self.assertRaises(h.Rejected):h.verify_remote('v4.0.0-beta.6','123',source)
if __name__=='__main__':unittest.main()

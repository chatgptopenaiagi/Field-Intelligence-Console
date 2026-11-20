"""Explicit synthetic Android SAF to exact hosted wheel/EXE exchange. No publication."""
import argparse,base64,binascii,hashlib,importlib.util,json,os,re,subprocess,tempfile,urllib.request,venv,zipfile
from pathlib import Path
REPOSITORY='chatgptopenaiagi/Field-Intelligence-Console'
LIMIT=32768
class Rejected(ValueError):pass
def require(ok,message):
    if not ok:raise Rejected(message)
def sha(data):return hashlib.sha256(data).hexdigest()
def document(data):
    require(len(data)<=LIMIT,'input size exceeded')
    def pairs(items):
        d={}
        for k,v in items:
            require(k not in d,'duplicate JSON key');d[k]=v
        return d
    value=json.loads(data.decode('utf-8'),object_pairs_hook=pairs,parse_constant=lambda _:(_ for _ in ()).throw(Rejected('nonfinite JSON')))
    def walk(x,n=0):
        require(n<=32,'JSON depth exceeded')
        if isinstance(x,dict):
            for v in x.values():walk(v,n+1)
        elif isinstance(x,list):
            for v in x:walk(v,n+1)
    walk(value);return value
def decode(value):
    require(isinstance(value,str) and len(value)<=4*((LIMIT+2)//3),'encoded input size exceeded')
    try:data=base64.b64decode(value,validate=True)
    except (ValueError,binascii.Error):raise Rejected('invalid base64') from None
    return data,document(data)
def inputs(event):
    i=event['inputs'];tag=i['release_tag'];run=i['candidate_run_id']
    require(bool(re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+-beta\.[0-9]+',tag)),'invalid tag')
    require(bool(re.fullmatch(r'[1-9][0-9]{0,19}',run)),'invalid run ID')
    values={k:decode(i[k+'_base64']) for k in ('network','report','android_receipt')}
    return tag,run,values
def validate_inputs(values,apk_hash):
    from field_intelligence_console import validate_network,validate_report
    network=values['network'][1];report=values['report'][1];receipt=values['android_receipt'][1]
    validate_network(network);validate_report(report)
    require(len(network['entities'])==2,'expected two explicitly selected synthetic contacts')
    require(receipt.get('synthetic_only') is True and receipt.get('status')=='PASS' and receipt.get('actual_saf_open_and_create_document') is True,'synthetic export attestation required')
    require(receipt.get('selected_export_count')==2 and receipt.get('report_import_count')==1,'export count mismatch')
    require(receipt.get('package')=='com.fieldconsole.debug' and receipt.get('installed_apk_sha256')==apk_hash,'observed APK hash mismatch')
    require(bool(re.fullmatch('[0-9a-f]{40}',receipt.get('source_sha',''))),'invalid observed source SHA')
    files={x['name']:x for x in receipt['files']};require(len(receipt['files'])==2 and set(files)=={'device-network.json','device-report.json'},'export file set')
    for kind in ('network','report'):
        raw=values[kind][0];f=files['device-'+kind+'.json'];require(f['sha256']==sha(raw) and f['bytes']==len(raw),'export hash mismatch')
    return receipt['source_sha']
def api(path):
    token=os.environ['GH_TOKEN'];request=urllib.request.Request('https://api.github.com/'+path,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'FIC-hosted-exchange'})
    with urllib.request.urlopen(request,timeout=30) as response:return json.load(response)
def verify_remote(tag,run,source):
    repo=api('repos/'+REPOSITORY);require(repo['full_name']==REPOSITORY,'intended original repository required')
    r=api('repos/'+REPOSITORY+'/actions/runs/'+run)
    require(r['status']=='completed' and r['conclusion']=='success' and r['head_sha']==source,'candidate run must succeed on exact source')
    require(r['path']=='.github/workflows/release.yml' and r['repository']['full_name']==REPOSITORY,'candidate workflow identity')
    ref=api('repos/'+REPOSITORY+'/git/ref/tags/'+tag);require(ref['object']['type']=='tag','annotated release tag required')
    obj=api('repos/'+REPOSITORY+'/git/tags/'+ref['object']['sha']);require(obj['object']['type']=='commit' and obj['object']['sha']==source,'release tag source mismatch')
    require(r['head_branch']==tag,'candidate run must use requested release tag')
def checked_assets(root,source,tag):
    m=json.loads((root/'release-manifest.json').read_text(encoding='utf-8-sig'))
    require(m['source_sha']==source and m['tag']==tag and tag=='v'+m['version'],'manifest source mismatch')
    version=m['version'];match=re.fullmatch(r'([0-9]+\.[0-9]+\.[0-9]+)-beta\.([0-9]+)',version)
    require(match is not None,'unsupported experimental version')
    python_version=match[1]+'b'+match[2];prefix='FieldIntelligenceConsole-'+version
    apk_name='FieldIntelligenceConsole-BETA-Android.apk';exe_name='FieldIntelligenceConsole-BETA-Windows-x64.exe';wheel_name='field_intelligence_console-'+python_version+'-py3-none-any.whl'
    sdist_name='field_intelligence_console-'+python_version+'.tar.gz';source_name=prefix+'-source.zip'
    allowed={apk_name,exe_name,wheel_name,sdist_name,source_name,'SBOM.cdx.json','THIRD_PARTY_NOTICES.txt','artifact-inventory.json','release-manifest.json','SHA256SUMS.txt','RELEASE_NOTES.md'}
    entries=list(root.iterdir());require(all(f.is_file() and not f.is_symlink() for f in entries),'unexpected asset directory or link')
    files={f.name:f for f in entries};require(set(files)==allowed,'exact eleven-asset allowlist required')
    checks={}
    for line in (root/'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
        h,n=line.split('  ',1);require(n not in checks and n==Path(n).name and re.fullmatch('[0-9a-f]{64}',h),'unsafe checksum entry');checks[n]=h
    require(set(checks)==allowed-{'SHA256SUMS.txt'},'checksum coverage mismatch')
    for n,h in checks.items():require(sha(files[n].read_bytes())==h,'artifact checksum mismatch')
    require(len(m['artifacts'])==9 and {x['name'] for x in m['artifacts']}==allowed-{'SHA256SUMS.txt','release-manifest.json'},'manifest coverage mismatch')
    for x in m['artifacts']:require(x['sha256']==checks[x['name']] and x['bytes']==files[x['name']].stat().st_size,'manifest hash mismatch')
    # Reuse the exact release gate rather than maintain a weaker parallel gate.
    spec=importlib.util.spec_from_file_location('fic_release_guard',Path(__file__).with_name('finalize_release.py'))
    guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard)
    guard.validate_receipts(m['android'],m['windows'],m['python'],source)
    guard.validate_signing(m['android']['artifacts'])
    debug=next(x for x in m['android']['artifacts'] if x['variant']=='debug')
    require(debug['sha256']==checks[apk_name] and debug['bytes']==files[apk_name].stat().st_size and debug['package']=='com.fieldconsole.debug' and debug['version']==version,'tested APK binding mismatch')
    pe=m['windows']['binary_metadata']
    require(pe['sha256']==checks[exe_name] and pe['bytes']==files[exe_name].stat().st_size and pe['machine']=='AMD64' and pe['subsystem']=='Windows GUI' and pe['product_version']==version,'tested EXE binding mismatch')
    py={x['name']:x for x in m['python']['artifacts']}
    for n in (wheel_name,sdist_name):require(py[n]['sha256']==checks[n] and py[n]['bytes']==files[n].stat().st_size,'tested Python binding mismatch')
    with zipfile.ZipFile(files[source_name]) as archive:require(archive.comment.decode('ascii')==source,'source archive commit mismatch')
    return m,files[apk_name],files[exe_name],files[wheel_name]
def child(command,cwd):
    env={k:v for k,v in os.environ.items() if k not in ('PYTHONPATH','PYTHONHOME','VIRTUAL_ENV','GH_TOKEN','GITHUB_TOKEN')}
    r=subprocess.run(command,cwd=cwd,env=env,capture_output=True,timeout=240)
    require(r.returncode==0,'isolated exchange stage failed; child output withheld')
def execute(assets,values,source,tag,output):
    manifest,apk,exe,wheel=checked_assets(assets,source,tag)
    # The checked-out core validates the selected input before any candidate process starts.
    observed=validate_inputs(values,sha(apk.read_bytes()))
    with tempfile.TemporaryDirectory(prefix='fic-synthetic-exchange-') as owned:
        t=Path(owned)
        for kind in ('network','report'):(t/('device-'+kind+'.json')).write_bytes(values[kind][0])
        for phase in ('first','readback'):
            venv.EnvBuilder(with_pip=True).create(t/phase);python=t/phase/'Scripts/python.exe'
            child([str(python),'-m','pip','install','--disable-pip-version-check',str(wheel)],t)
            child([str(python),str(Path(__file__).resolve()),'--worker',phase],t)
            if phase=='first':
                child([str(exe),'--ui-test','--profile',str(t/'profile'),'--evidence',str(t/'ui.json'),'--interop-input',str(t/'wheel-network.json'),'--interop-output',str(t/'windows-network.json'),'--interop-report-input',str(t/'wheel-report.json'),'--interop-report-output',str(t/'windows-report.json')],t)
                ui=json.loads((t/'ui.json').read_text(encoding='utf-8'));require(ui['status']=='PASS' and ui['frozen'] and ui['check_count']>=28,'packaged GUI gate failed')
        receipt={'status':'PASS','source_sha':source,'observed_android_export_source_sha':observed,'apk_sha256':sha(apk.read_bytes()),'exe_sha256':sha(exe.read_bytes()),'wheel_sha256':sha(wheel.read_bytes()),'input_hashes':{k:sha(v[0]) for k,v in values.items()},'entities':2,'reports':1,'packaged_ui_checks':ui['check_count'],'exact_entities_and_ancestry':True,'exact_report':True,'steps':['fresh wheel install outside checkout','atomic import/reimport/reopen/export','exact packaged EXE GUI preview/confirm/export','second fresh installed wheel exact readback'],'limitations':['Explicit synthetic inputs only; receipt is an export attestation, not new Android runtime execution.','Observed export source is retained independently; exact APK bytes bind its relevance.','Separate release build native seven-check gate remains required.','No physical device, personal contacts or live provider coverage claimed.']}
        output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
def worker(phase):
    import field_intelligence_console as fic
    require('site-packages' in fic.__file__,'wheel imported from source checkout')
    n=json.loads(Path('device-network.json').read_text(encoding='utf-8'));r=json.loads(Path('device-report.json').read_text(encoding='utf-8'));fic.validate_network(n);fic.validate_report(r)
    if phase=='first':
        preview=fic.parse_import(Path('device-network.json').read_bytes(),'json');require(preview.valid_count==2 and preview.invalid_count==0,'preview invalid')
        with fic.Store('synthetic.sqlite') as store:
            require(store.import_preview(preview,[e['id'] for e in preview.entities])['inserted']==2,'import count')
            require(store.import_preview(preview,[e['id'] for e in preview.entities])['duplicates']==2,'duplicate count');store.save_report(r)
        with fic.Store('synthetic.sqlite') as store:
            entities=store.list_entities();require(sorted(entities,key=lambda e:e['id'])==sorted(n['entities'],key=lambda e:e['id']),'reopen entities');require(store.get_report(r['id'])==r,'reopen report')
            Path('wheel-network.json').write_bytes(fic.export_preview(entities,'json').data);Path('wheel-report.json').write_text(json.dumps(r),encoding='utf-8')
    else:
        returned=json.loads(Path('windows-network.json').read_text(encoding='utf-8'));report=json.loads(Path('windows-report.json').read_text(encoding='utf-8'));fic.validate_network(returned);fic.validate_report(report)
        require(sorted(returned['entities'],key=lambda e:e['id'])==sorted(n['entities'],key=lambda e:e['id']),'returned entities');require(report==r,'returned report')
def main():
    p=argparse.ArgumentParser();p.add_argument('--worker',choices=['first','readback']);p.add_argument('--preflight',action='store_true');p.add_argument('--assets',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
    if a.worker:worker(a.worker);return
    event=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text(encoding='utf-8'));tag,run,values=inputs(event)
    source=subprocess.check_output(['git','rev-parse','HEAD'],text=True,encoding='utf-8').strip();verify_remote(tag,run,source)
    if a.preflight:
        with open(os.environ['GITHUB_OUTPUT'],'a',encoding='utf-8') as f:f.write('candidate_run_id='+run+'\n')
    else:execute(a.assets.resolve(),values,source,tag,a.output)
if __name__=='__main__':
    try:main()
    except Exception as e:
        print('Hosted exchange FAILED: '+(str(e) if isinstance(e,Rejected) else type(e).__name__));raise SystemExit(1)

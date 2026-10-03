"""Validate real Android codec exports using a newly installed wheel, then export
the selected synthetic documents for the packaged Windows runtime acceptance gate.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import venv


CHECK = r'''
import hashlib
import json
from pathlib import Path
import field_intelligence_console as fic
assert 'site-packages' in fic.__file__
receipt=[]
for name in ('android-network.json','android-vcf-network.json','android-report.json'):
    data=Path(name).read_bytes()
    document=json.loads(data)
    if document['schema']=='fic.network':
        fic.validate_network(document)
        preview=fic.parse_import(data,'json')
        assert preview.invalid_count==0
        with fic.Store(name+'.synthetic.sqlite') as store:
            selected=[e['id'] for e in preview.entities]
            assert store.import_preview(preview,selected)['inserted']==len(selected)
            assert store.import_preview(preview,selected)['duplicates']==len(selected)
        with fic.Store(name+'.synthetic.sqlite') as store:
            actual=store.list_entities()
            assert sorted(actual,key=lambda e:e['id'])==sorted(document['entities'],key=lambda e:e['id'])
            output=fic.export_preview(actual,'json').data
            fic.validate_network(json.loads(output))
        outname=name.replace('android-','python-roundtrip-')
        count=len(preview.entities)
    else:
        fic.validate_report(document)
        with fic.Store('report.synthetic.sqlite') as store:
            store.save_report(document)
        with fic.Store('report.synthetic.sqlite') as store:
            actual=store.get_report(document['id'])
            assert actual==document
            output=json.dumps(actual,ensure_ascii=False,allow_nan=False,indent=2).encode('utf-8')
        outname='python-roundtrip-report.json'
        count=1
    Path(outname).write_bytes(output)
    receipt.append({'input':name,'output':outname,'records':count,'strict_validation':'PASS','repeat_import':'PASS' if document['schema']=='fic.network' else 'not applicable','reopen_preserved':'PASS','input_sha256':hashlib.sha256(data).hexdigest(),'output_sha256':hashlib.sha256(output).hexdigest()})
Path('receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
print(json.dumps({'installed_wheel_android_roundtrip':'PASS','files':len(receipt)}))
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wheel", required=True)
    parser.add_argument("--android-dir", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    wheel = Path(args.wheel).resolve(strict=True)
    source = Path(args.android_dir).resolve(strict=True)
    output = Path(args.output_dir).resolve()
    names = ("android-network.json", "android-vcf-network.json", "android-report.json")
    if any(not (source / name).is_file() for name in names):
        print("BLOCKED: all three actual Android codec exports are required")
        return 2
    if output.exists() and any(output.iterdir()):
        print("BLOCKED: select a new empty output directory to preserve prior evidence")
        return 2
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    env.pop("PYTHONHOME", None)
    with tempfile.TemporaryDirectory(prefix="fic-interop-wheel-") as owned:
        root = Path(owned)
        for name in names:
            shutil.copyfile(source / name, root / name)
        venv.EnvBuilder(with_pip=True).create(root / "venv")
        python = root / "venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        for label, command in (("install", [str(python), "-m", "pip", "install", "--disable-pip-version-check", str(wheel)]),
                               ("roundtrip", [str(python), "-c", CHECK])):
            result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True, timeout=180)
            if result.returncode:
                print(json.dumps({"phase": label, "status": "FAIL", "exit_code": result.returncode}))
                return 1
        output.mkdir(parents=True, exist_ok=True)
        for name in ("python-roundtrip-network.json", "python-roundtrip-vcf-network.json", "python-roundtrip-report.json", "receipt.json"):
            shutil.copyfile(root / name, output / name)
        print(json.dumps({"installed_wheel_android_roundtrip": "PASS", "files": 3}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Build-gate helper: install the real wheel outside the checkout and exercise it.

Creates only an owned temporary directory; it contains synthetic test files and is
removed by TemporaryDirectory. No PYTHONPATH, no editable install, no production data.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import venv


SMOKE = r'''
from importlib import metadata, resources
import json
from pathlib import Path
import field_intelligence_console as fic
assert 'site-packages' in fic.__file__, 'Source checkout imported accidentally'
resource = resources.files('field_intelligence_console')
data = resource.joinpath('fixtures', 'contacts.vcf').read_bytes()
preview = fic.parse_import(data, 'vcf')
assert preview.valid_count == 2 and preview.invalid_count == 0
Path('synthetic.vcf').write_bytes(data)
with fic.Store('synthetic.sqlite') as store:
    assert store.list_entities() == []
    assert store.import_preview(preview, [])['inserted'] == 0
    assert store.import_preview(preview, [e['id'] for e in preview.entities])['inserted'] == 2
    assert store.import_preview(preview, [e['id'] for e in preview.entities])['duplicates'] == 2
with fic.Store('synthetic.sqlite') as store:
    entities = store.list_entities()
    assert len(entities) == 2
    assert fic.query_network(entities, 'city', {'locality':'Example City','country':'Example Country'})['count'] == 1
    Path('network.json').write_bytes(fic.export_preview(entities, 'json').data)
    assert fic.parse_import(Path('network.json').read_bytes(), 'json').entities == entities
    report = json.loads(resource.joinpath('fixtures', 'field-report-v1.json').read_text('utf-8'))
    fic.validate_report(report)
    store.save_report(report)
    assert store.get_report(report['id']) == report
    assert 'network' not in fic.ai_packet(report)
assert metadata.version('field-intelligence-console') == '4.0.0b1'
for case in json.loads(resource.joinpath('fixtures','invalid-contracts.json').read_text('utf-8'))['cases']:
    document=json.loads(resource.joinpath('fixtures',case['fixture']).read_text('utf-8'))
    target=document
    for key in case['path'][:-1]: target=target[key]
    if case.get('remove'): del target[case['path'][-1]]
    else: target[case['path'][-1]]=case['value']
    try:
        (fic.validate_network if case['fixture']=='network-v1.json' else fic.validate_report)(document)
    except fic.ValidationError: pass
    else: raise AssertionError('Invalid shared contract accepted: '+case['name'])
print(json.dumps({'wheel_import':'PASS','packaged_resources':'PASS','records':2,'repeat_duplicates':2,'persistence':'PASS','geography':'PASS','report_roundtrip':'PASS'}))
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("wheel")
    args = parser.parse_args()
    wheel = Path(args.wheel).resolve(strict=True)
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    env.pop("PYTHONHOME", None)
    with tempfile.TemporaryDirectory(prefix="fic-wheel-isolated-") as owned:
        root = Path(owned).resolve()
        venv.EnvBuilder(with_pip=True).create(root / "venv")
        python = root / "venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        checks = [
            ("clean-install", [str(python), "-m", "pip", "install", "--disable-pip-version-check", str(wheel)]),
            ("installed-workflows", [str(python), "-c", SMOKE]),
            ("cli-validate", [str(python), "-m", "field_intelligence_console", "validate", "network.json"]),
            ("cli-convert", [str(python), "-m", "field_intelligence_console", "convert", "synthetic.vcf", "--from", "vcf", "--to", "json", "--select", "ALL_VALID", "--output", "cli-network.json"]),
            ("cli-search", [str(python), "-m", "field_intelligence_console", "search", "--database", "synthetic.sqlite", "--text", "Example", "--output", "cli-search.json"]),
        ]
        for name, command in checks:
            result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True, timeout=180)
            if result.returncode:
                print(json.dumps({"check": name, "status": "FAIL", "exit_code": result.returncode}))
                return 1
            print(json.dumps({"check": name, "status": "PASS"}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

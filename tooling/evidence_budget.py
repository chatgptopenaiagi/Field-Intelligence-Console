"""Check project-owned evidence limits without deleting or rewriting any evidence."""
from pathlib import Path
import argparse
import json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".local/evidence")
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    root = Path(args.root).resolve()
    require_root = project / ".local"
    if not root.is_relative_to(require_root) or root == require_root:
        raise ValueError("Only an explicit project-local evidence subdirectory is supported")
    total = 0
    oversized_logs = 0
    for path in root.rglob("*") if root.exists() else []:
        if path.is_symlink():
            raise ValueError("Evidence symlink requires explicit review")
        if path.is_file():
            size = path.stat().st_size
            total += size
            oversized_logs += int(path.suffix == ".log" and size > 20 * 1024 * 1024)
    ok = total <= 8 * 1024**3 and oversized_logs == 0
    print(json.dumps({"status": "PASS" if ok else "BUDGET_EXCEEDED", "bytes": total,
                      "limit_bytes": 8 * 1024**3, "logs_over_20_mib": oversized_logs,
                      "action": "Read-only check; no evidence deleted"}))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

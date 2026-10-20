"""
מעתיק את THIRD_PARTY_NOTICES.md ואת legal/licenses/*.txt לאפליקציה (assets/legal), לתוסף (extension/legal) ולאתר (site/legal).
להריץ אחרי כל שינוי בהם: python tools/sync_legal.py. ה-CI מריץ עם --check ונכשל אם העותקים לא מעודכנים.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_NOTICES = ROOT / "THIRD_PARTY_NOTICES.md"
SRC_LICENSES = ROOT / "legal" / "licenses"
TARGETS = [ROOT / "android/app/src/main/assets/legal", ROOT / "extension/legal", ROOT / "site/legal"]


def expected(target: Path) -> dict[Path, bytes]:
    files = sorted(p.name for p in SRC_LICENSES.glob("*.txt"))
    out = {target / "THIRD_PARTY_NOTICES.md": SRC_NOTICES.read_bytes()}
    for name in files:
        out[target / "licenses" / name] = (SRC_LICENSES / name).read_bytes()
    out[target / "index.json"] = (json.dumps(files, indent=2) + "\n").encode()
    return out


def main() -> int:
    check = "--check" in sys.argv
    stale = []
    for target in TARGETS:
        want = expected(target)
        have = {p for p in (target / "licenses").glob("*.txt")} if (target / "licenses").exists() else set()
        for extra in have - set(want):
            stale.append(extra)
            if not check:
                extra.unlink()
        for path, data in want.items():
            if not path.exists() or path.read_bytes() != data:
                stale.append(path)
                if not check:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
    if check and stale:
        print("Out of date (run python tools/sync_legal.py):", *map(str, stale), sep="\n  ")
        return 1
    print("legal files in sync" if not stale else f"updated {len(stale)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())

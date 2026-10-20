"""מייצא את הקוד לריפו הציבורי trenk-source (GPL-3.0) וסורק אותו לפני שהוא עולה.

  python tools/export_public.py <תיקיית-יעד>

רק קבצים שב-git, רק התיקיות שב-INCLUDE. config/ מוחלף בערכים לדוגמה (הערכים האמיתיים מגיעים מ-Secrets בזמן הבנייה).
הסריקה נכשלת (exit 1) אם נמצא מפתח, טוקן, מייל, נתיב מקומי או ערך פרטי.
"""
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INCLUDE = ("engine/", "extension/", "android/", "site/", "packaging/", "legal/", "tools/", ".github/",
           "THIRD_PARTY_NOTICES.md", "LICENSE", ".gitignore", ".gitattributes")
EXAMPLE_CONFIG = {
    "config/site.json": {"url": "https://levimtrenk-jpg.github.io/trenk/", "contact_email": "contact@example.com", "sentry_dsn": ""},
    "config/stats.json": {"ntfy_topic": "your-ntfy-topic", "goatcounter": "https://trenk.goatcounter.com"},
    "config/tip.json": {"url": ""},
}
# טקסטים של רישיונות (מכילים מיילים של המחברים, למשל FSF) – לא נסרקים למיילים
LICENSE_TEXTS = re.compile(r"(^|/)legal/licenses/|^LICENSE$")
ALLOWED_EMAILS = re.compile(r"@example\.com$|@users\.noreply\.github\.com$|^git@github\.com$")
# הסורק עצמו מכיל את התבניות שהוא מחפש
SELF = "tools/export_public.py"


def private_values():
    """הערכים האמיתיים מהריפו הפרטי: אסור שיופיעו בייצוא"""
    vals = []
    for name in ("site.json", "stats.json"):
        f = ROOT / "config" / name
        if f.exists():
            d = json.loads(f.read_text(encoding="utf-8"))
            vals += [d.get(k) for k in ("contact_email", "sentry_dsn", "ntfy_topic")]
    return [v for v in vals if v]


def private_terms():
    """שמות ושם משתמש שאסור שיופיעו. לא כתובים כאן (הקובץ עצמו ציבורי): מגיעים מ-SCAN_PRIVATE_TERMS
    (Secret, מופרד בפסיקים) ומשם המשתמש המקומי. מילה שלמה בלבד, כך ששם הריפו ב-GitHub לא נתפס."""
    terms = [t.strip() for t in os.environ.get("SCAN_PRIVATE_TERMS", "").split(",") if t.strip()]
    if Path.home().name not in ("runner", "root"):
        terms.append(Path.home().name)
    return [re.compile(r"(?i)(?<![\w֐-׿])" + re.escape(t) + r"(?![\w֐-׿])") for t in terms]


PATTERNS = [
    ("private key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("GitHub token", re.compile(r"\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})")),
    ("AWS key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("Google API key", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b")),
    ("Slack token", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}")),
    ("API key", re.compile(r"\bsk-[A-Za-z0-9_\-]{20,}")),
    ("Sentry DSN", re.compile(r"https://[0-9a-f]{32}@[\w.]*ingest[\w.]*sentry\.io")),
    ("password", re.compile(r"(?i)(password|passwd|secret|token)\s*[:=]\s*[\"'][^\"'\s$]{6,}[\"']")),
    ("local path", re.compile(r"(?i)[A-Z]:[\\/]+Users[\\/]|/home/[a-z]")),
    ("phone", re.compile(r"(?<!\d)(\+972|0)5\d[- ]?\d{3}[- ]?\d{4}(?!\d)")),
    ("IBAN", re.compile(r"\bIL\d{2}[ ]?\d{4}[ ]?\d{4}")),
]
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def scan(out: Path):
    secrets = private_values()
    terms = private_terms()
    if not terms:
        sys.exit("SCAN_PRIVATE_TERMS is empty: refusing to export without a name/username check")
    findings = []
    for f in sorted(p for p in out.rglob("*") if p.is_file() and ".git" not in p.relative_to(out).parts):
        rel = f.relative_to(out).as_posix()
        data = f.read_bytes()
        if b"\0" in data[:4096]:
            # קובץ בינארי: בודקים רק ערכים פרטיים, נתיבים ושמות
            text = data.decode("latin-1")
            if any(v in text for v in secrets + ["C:\\Users"]) or any(rx.search(text) for rx in terms):
                findings.append((rel, 0, "binary contains private value"))
            continue
        for n, line in enumerate(data.decode("utf-8", "replace").splitlines(), 1):
            for v in secrets:
                if v in line:
                    findings.append((rel, n, "private config value"))
            for name, rx in PATTERNS if rel != SELF else ():
                if rx.search(line):
                    findings.append((rel, n, name))
            if any(rx.search(line) for rx in terms):
                findings.append((rel, n, "name / username"))
            if not LICENSE_TEXTS.search(rel):
                for m in EMAIL.finditer(line):
                    if not ALLOWED_EMAILS.search(m.group()) and not re.search(r"\.(png|svg|jpg|webp)$", m.group()):
                        findings.append((rel, n, "email " + m.group()))
    return findings


def export(out: Path):
    if out.exists():
        for p in out.iterdir():
            if p.name != ".git":
                shutil.rmtree(p) if p.is_dir() else p.unlink()
    out.mkdir(parents=True, exist_ok=True)
    files = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True).stdout.decode().split("\0")
    for rel in filter(None, files):
        if rel.startswith(INCLUDE) or rel in INCLUDE:
            # deploy key לא יכול לכתוב ל-.github/workflows (GitHub דוחה), וממילא ה-CI רץ רק בריפו הפרטי:
            # בריפו הציבורי קובצי ה-CI נמצאים ב-ci/github/, לעיון בלבד
            dst = out / re.sub(r"^\.github/", "ci/github/", rel)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / rel, dst)
    for rel, data in EXAMPLE_CONFIG.items():
        (out / rel).parent.mkdir(parents=True, exist_ok=True)
        (out / rel).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    shutil.copy2(ROOT / "tools/public/README.md", out / "README.md")
    shutil.rmtree(out / "tools/public", ignore_errors=True)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    target = Path(sys.argv[1]).resolve()
    export(target)
    found = scan(target)
    for rel, n, what in found:
        print(f"FOUND {rel}:{n}: {what}")
    count = sum(1 for p in target.rglob("*") if p.is_file() and ".git" not in p.relative_to(target).parts)
    print(f"{count} files exported to {target}; {len(found)} findings")
    sys.exit(1 if found else 0)

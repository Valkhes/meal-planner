#!/usr/bin/env python3
"""Docs say what is true now: every repository path a Markdown file names in backticks must exist.

A backticked token counts as a path when it contains a slash and starts with a top-level entry of
the repository (`tools/check.sh`, `domain/src/...`). Placeholders such as `<module>` are skipped.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOKEN = re.compile(r"`([^`\s]+)`")


def main() -> int:
    top = {p.name for p in ROOT.iterdir() if not p.name.startswith(".") or p.name in (".claude", ".github")}
    errors = []
    docs = [p for p in ROOT.rglob("*.md") if "/build/" not in p.as_posix() and "/.gradle/" not in p.as_posix()]
    for doc in docs:
        for number, line in enumerate(doc.read_text(encoding="utf-8").splitlines(), 1):
            for token in TOKEN.findall(line):
                path = token.rstrip(".,:;)").split(":")[0]
                if "/" not in path or "<" in path or "*" in path or path.startswith("http"):
                    continue
                if path.split("/")[0] not in top:
                    continue
                if not (ROOT / path).exists():
                    errors.append(f"{doc.relative_to(ROOT)}:{number}: `{token}` does not exist")
    for e in errors:
        print(f"doc_refs: {e}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

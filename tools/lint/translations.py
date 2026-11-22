#!/usr/bin/env python3
"""English first, French native (README, constraint 8): every English string has a French one.

For each `values/strings.xml` (Compose resources and Android resources), the `values-fr/` file next
to it must define the same strings, plurals and string arrays, with the same placeholders. Exits 1
on the first difference it reports.
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
PLACEHOLDER = re.compile(r"%(\d+\$)?[sd]")


def entries(path: pathlib.Path) -> dict[str, tuple[str, list[str]]]:
    """name -> (kind, texts)."""
    out = {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if el.tag == "string":
            out[name] = ("string", [el.text or ""])
        elif el.tag in ("plurals", "string-array"):
            out[name] = (el.tag, [item.text or "" for item in el])
    return out


def placeholders(texts: list[str]) -> set[str]:
    return {m.group(0) for t in texts for m in PLACEHOLDER.finditer(t)}


def main() -> int:
    errors = []
    files = [p for p in ROOT.glob("*/src/**/values/strings.xml") if "/build/" not in p.as_posix()]
    if not files:
        errors.append("no values/strings.xml found")
    for en_path in files:
        fr_path = en_path.parent.parent / "values-fr" / "strings.xml"
        rel = en_path.relative_to(ROOT)
        if not fr_path.exists():
            errors.append(f"{rel}: no values-fr/strings.xml next to it")
            continue
        en, fr = entries(en_path), entries(fr_path)
        for name in sorted(en.keys() - fr.keys()):
            errors.append(f"{rel}: '{name}' has no French translation")
        for name in sorted(fr.keys() - en.keys()):
            errors.append(f"{rel}: '{name}' exists only in French")
        for name in sorted(en.keys() & fr.keys()):
            (en_kind, en_texts), (fr_kind, fr_texts) = en[name], fr[name]
            if en_kind != fr_kind:
                errors.append(f"{rel}: '{name}' is a {en_kind} in English but a {fr_kind} in French")
            elif en_kind == "string-array" and len(en_texts) != len(fr_texts):
                errors.append(f"{rel}: '{name}' has {len(en_texts)} items in English, {len(fr_texts)} in French")
            elif placeholders(en_texts) != placeholders(fr_texts):
                errors.append(f"{rel}: '{name}' placeholders differ: {sorted(placeholders(en_texts))} vs {sorted(placeholders(fr_texts))}")
    for e in errors:
        print(f"translations: {e}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

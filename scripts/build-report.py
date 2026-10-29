"""Convert this repository's README to safe Typst markup and compile the report.

Supported Markdown is deliberately small: headings, paragraphs, fenced code, tables,
ordered lists, links and images. It covers README.md without network packages.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def quote(value):
    return json.dumps(value, ensure_ascii=False)


def inline(text):
    pattern = r"\[([^\]]+)\]\(([^)]+)\)|`([^`]+)`|https?://[^\s<>]+"
    result = []
    start = 0
    for match in re.finditer(pattern, text):
        if match.start() > start:
            result.append("#text(" + quote(text[start:match.start()]) + ")")
        if match.group(1):
            destination = match.group(2)
            if not destination.startswith("http"):
                destination = "https://github.com/bakuani/security_lab1/blob/main/" + destination
            result.append("#link(" + quote(destination) + ")[#text(" + quote(match.group(1)) + ")]")
        elif match.group(3):
            result.append("#raw(" + quote(match.group(3)) + ")")
        else:
            url = match.group(0).rstrip(".,;:!?")
            suffix = match.group(0)[len(url):]
            result.append("#link(" + quote(url) + ")[#text(" + quote(url) + ")]#text(" + quote(suffix) + ")")
        start = match.end()
    if start < len(text):
        result.append("#text(" + quote(text[start:]) + ")")
    return "".join(result)


def convert(source):
    lines = source.splitlines()
    out = ["// Generated from README.md by scripts/build-report.py; edit README.md instead.\n"]
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith("```"):
            language = line[3:].strip()
            block = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                block.append(lines[i])
                i += 1
            out.append("#raw(" + quote("\n".join(block)) + ", block: true, lang: " + quote(language) + ")\n")
        elif line.startswith("#"):
            level = len(line) - len(line.lstrip("#")) + 1
            out.append("=" * level + " " + inline(line.lstrip("# ")) + "\n")
        elif line.startswith("!["):
            match = re.fullmatch(r"!\[([^\]]+)\]\(([^)]+)\)", line)
            if not match:
                raise ValueError("Unsupported image Markdown: " + line)
            path = (ROOT / match.group(2)).resolve()
            relative = path.relative_to(ROOT / "report").as_posix()
            out.append("#figure(image(" + quote(relative) + ", width: 100%), caption: [" + inline(match.group(1)) + "])\n")
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            count = len(rows[0])
            widths = "(0.6fr, 1.7fr, 1fr, 1.8fr)" if count == 4 else "(" + ", ".join(["1fr"] * count) + ")"
            head = ", ".join("[" + inline(cell) + "]" for cell in rows[0])
            body = ",\n".join(", ".join("[" + inline(cell) + "]" for cell in row) for row in rows[1:])
            out.append("#block[\n#set text(size: 10pt)\n#table(columns: " + widths +
                       ", inset: 5pt, stroke: 0.4pt + luma(180), table.header(" + head + "),\n" + body + ")\n]\n")
            continue
        elif re.match(r"\d+\. ", line):
            out.append("+ " + inline(re.sub(r"^\d+\. ", "", line)) + "\n")
        else:
            paragraph = [line]
            while i + 1 < len(lines) and lines[i + 1].strip() and not re.match(r"^(#|```|\||!\[|\d+\. )", lines[i + 1]):
                i += 1
                paragraph.append(lines[i])
            out.append(inline(" ".join(paragraph)) + "\n")
        i += 1
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pipeline-url", help="URL of the latest successful GitHub Actions run")
    parser.add_argument("--source-only", action="store_true")
    args = parser.parse_args()
    report = ROOT / "report"
    report.mkdir(exist_ok=True)
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    (report / "readme.typ").write_text(convert(readme), encoding="utf-8")
    if not args.source_only:
        typst = shutil.which("typst")
        if not typst:
            raise SystemExit("Typst is required: https://github.com/typst/typst/releases")
        command = [typst, "compile", "--root", str(ROOT)]
        if args.pipeline_url:
            command += ["--input", "pipeline-url=" + args.pipeline_url]
        command += [str(report / "report.typ"), str(report / "report.pdf")]
        subprocess.run(command, cwd=ROOT, check=True)
        print("Report compiled: report/report.pdf")


if __name__ == "__main__":
    main()

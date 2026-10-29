"""Summarize scanner-produced reports without changing scan results or exit codes."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def sast():
    report = Path("target/spotbugsXml.xml")
    if not report.exists():
        print("## SAST\nNo scanner report was produced. Inspect the failed step.")
        return
    root = ET.parse(report).getroot()
    bugs = root.findall("BugInstance")
    print("## SAST — SpotBugs + Find Security Bugs\n")
    print(f"Findings: **{len(bugs)}**. Threshold: Low; effort: Max.\n")
    print("| Check | Findings |\n|---|---:|")
    for priority in (1, 2, 3):
        print(f"| Priority {priority} | {sum(b.get('priority') == str(priority) for b in bugs)} |")
    tests = [ET.parse(p).getroot() for p in Path("target/surefire-reports").glob("TEST-*.xml")]
    if tests:
        print("\nIntegration tests: **{}**, failures: **{}**, errors: **{}**.".format(
            sum(int(t.get("tests", 0)) for t in tests),
            sum(int(t.get("failures", 0)) for t in tests),
            sum(int(t.get("errors", 0)) for t in tests)))


def sca():
    report = Path("target/dependency-check-report.json")
    if not report.exists():
        print("## SCA\nNo scanner report was produced. Inspect the failed step.")
        return
    data = json.loads(report.read_text(encoding="utf-8"))
    dependencies = data.get("dependencies", [])
    vulnerable = [d for d in dependencies if d.get("vulnerabilities")]
    cves = {v["name"] for d in vulnerable for v in d["vulnerabilities"]}
    print("## SCA — OWASP Dependency-Check\n")
    print("| Metric | Value |\n|---|---:|")
    print(f"| Dependencies analyzed | {len(dependencies)} |")
    print(f"| Vulnerable dependencies | {len(vulnerable)} |")
    print(f"| Unique vulnerabilities | {len(cves)} |")
    print("\nBuild fails for **CVSS ≥ 7.0** or scanner errors. Data source: official NVD 2.0 feeds.")
    for d in vulnerable:
        print(f"\n- `{d['fileName']}`: " + ", ".join(v["name"] for v in d["vulnerabilities"]))


if __name__ == "__main__":
    {"sast": sast, "sca": sca}[sys.argv[1]]()

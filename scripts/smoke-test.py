"""Exercise the running API with real curl requests; optionally start the packaged JAR."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--start", action="store_true", help="Start and stop target/security-lab1-1.0.0.jar")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    env = dict(os.environ)
    process = None
    log = None
    output = ROOT / "target" / "smoke"
    output.mkdir(parents=True, exist_ok=True)
    results = []
    if args.start:
        env.update(LAB_JWT_SECRET=secrets.token_hex(48), LAB_ALICE_PASSWORD=secrets.token_urlsafe(24),
                   LAB_BOB_PASSWORD=secrets.token_urlsafe(24), DEBUG="false")
        java = str(Path(env["JAVA_HOME"]) / "bin" / "java") if env.get("JAVA_HOME") else "java"
        log = (output / "server.log").open("w", encoding="utf-8")
        process = subprocess.Popen([java, "-jar", str(ROOT / "target/security-lab1-1.0.0.jar")],
                                   env=env, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    curl = shutil.which("curl.exe") or shutil.which("curl")
    if not curl:
        raise RuntimeError("curl is required")

    def request(name, method, path, expected, body=None, token=None, redact=False):
        command = [curl, "--silent", "--show-error", "--max-time", "15", "--request", method,
                   args.base_url + path, "--write-out", "\n%{http_code}"]
        if token:
            command += ["--header", "Authorization: Bearer " + token]
        if body is not None:
            command += ["--header", "Content-Type: application/json", "--data-binary", "@-"]
        completed = subprocess.run(command, input=json.dumps(body) if body is not None else None,
                                   text=True, capture_output=True, encoding="utf-8", check=True)
        raw, status = completed.stdout.rsplit("\n", 1)
        data = json.loads(raw)
        assert int(status) == expected, f"{name}: expected {expected}, got {status}: {raw}"
        public = {k: v for k, v in data.items() if k != "accessToken"} if redact else data
        results.append({"check": name, "method": method, "path": path, "status": int(status), "response": public})
        print(f"PASS {method} {path} -> {status} ({name})")
        return data

    try:
        if process:
            for _ in range(90):
                if process.poll() is not None:
                    raise RuntimeError("Server exited; inspect target/smoke/server.log")
                try:
                    urllib.request.urlopen(args.base_url + "/api/data", timeout=1)
                except urllib.error.HTTPError as error:
                    if error.code == 401:
                        break
                except (urllib.error.URLError, TimeoutError):
                    pass
                time.sleep(0.5)
            else:
                raise RuntimeError("Server startup timed out")
        request("no JWT", "GET", "/api/data", 401)
        token = request("login", "POST", "/auth/login", 200,
                        {"username": "alice", "password": env["LAB_ALICE_PASSWORD"]}, redact=True)["accessToken"]
        request("wrong password", "POST", "/auth/login", 401,
                {"username": "alice", "password": "incorrect-demo-password"})
        request("SQL injection rejected", "POST", "/auth/login", 401,
                {"username": "alice' OR '1'='1' --", "password": "incorrect-demo-password"})
        tasks = request("owner-scoped list", "GET", "/api/data", 200, token=token)
        assert len(tasks) == 2 and "&lt;script&gt;" in tasks[1]["title"]
        assert "<script>" not in json.dumps(tasks) and "&lt;img" in tasks[1]["description"]
        request("change own task", "PATCH", f"/api/data/{tasks[0]['id']}/status", 200, {"status": "DONE"}, token)
        after = request("status saved", "GET", "/api/data", 200, token=token)
        assert after[0]["status"] == "DONE"
        bob_token = request("second user login", "POST", "/auth/login", 200,
                            {"username": "bob", "password": env["LAB_BOB_PASSWORD"]}, redact=True)["accessToken"]
        bob_tasks = request("second user's list", "GET", "/api/data", 200, token=bob_token)
        assert len(bob_tasks) == 1
        request("foreign task denied", "PATCH", f"/api/data/{bob_tasks[0]['id']}/status", 404, {"status": "DONE"}, token)
        request("status validation", "PATCH", f"/api/data/{tasks[0]['id']}/status", 400, {"status": "<script>"}, token)
        request("invalid JWT", "GET", "/api/data", 401, token="invalid.jwt.token")
        (output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
        lines = ["# Проверка API через curl", "", "Все запросы выполнены скриптом scripts/smoke-test.py.",
                 "Пароли и JWT в протокол не включены.", "", "| Проверка | Запрос | HTTP |", "|---|---|---:|"]
        lines += [f"| {r['check']} | {r['method']} {r['path']} | {r['status']} |" for r in results]
        (output / "transcript.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"All {len(results)} curl checks passed. Reports: target/smoke/")
    finally:
        if process:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if log:
            log.close()


if __name__ == "__main__":
    main()

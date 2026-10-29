#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
new_secret() { od -An -N48 -tx1 /dev/urandom | tr -d ' \n'; }
export LAB_JWT_SECRET="${LAB_JWT_SECRET:-$(new_secret)}"
export LAB_ALICE_PASSWORD="${LAB_ALICE_PASSWORD:-$(new_secret | cut -c1-24)}"
export LAB_BOB_PASSWORD="${LAB_BOB_PASSWORD:-$(new_secret | cut -c1-24)}"
printf 'Local demo: http://127.0.0.1:8080 (data is reset on restart)\nalice password: %s\nbob password:   %s\n' "$LAB_ALICE_PASSWORD" "$LAB_BOB_PASSWORD"
exec ./mvnw spring-boot:run

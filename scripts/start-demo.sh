#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
export LAB_JWT_SECRET="${LAB_JWT_SECRET:-$(od -An -N48 -tx1 /dev/urandom | tr -d ' \n')}"
# Passwords for the local demo; only BCrypt hashes are stored in the database.
export LAB_ALICE_PASSWORD="${LAB_ALICE_PASSWORD:-AliceDemo2026!}"
export LAB_BOB_PASSWORD="${LAB_BOB_PASSWORD:-BobDemo2026!}"
exec ./mvnw spring-boot:run

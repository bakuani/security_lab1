#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
export LAB_JWT_SECRET="${LAB_JWT_SECRET:-$(od -An -N48 -tx1 /dev/urandom | tr -d ' \n')}"
# Passwords for the local demo; only BCrypt hashes are stored in the database.
export LAB_ANNA_PASSWORD="${LAB_ANNA_PASSWORD:-AnnaDemo2026!}"
export LAB_IVAN_PASSWORD="${LAB_IVAN_PASSWORD:-IvanDemo2026!}"
exec ./mvnw spring-boot:run

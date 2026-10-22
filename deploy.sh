#!/usr/bin/env bash
# Pulls main, compiles shaded jar, puts it in update/.
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "${ROOT}"

UPDATE_DIR="${1:-${ROOT}/update}"
mkdir -p -- "${UPDATE_DIR}"

git fetch origin main
git checkout main
git pull --ff-only origin main

./build.sh

JAR="$(ls -1t target/bot-*.jar 2>/dev/null | grep -v '/original-' | head -1 || true)"
if [[ -z "${JAR}" ]]; then
	echo "no shaded jar in target/ (expected bot-*.jar, not original-*)" >&2
	exit 1
fi

cp -f -- "${JAR}" "${UPDATE_DIR}/bot.jar.partial"
mv -f -- "${UPDATE_DIR}/bot.jar.partial" "${UPDATE_DIR}/bot.jar"
echo "wrote ${UPDATE_DIR}/bot.jar"

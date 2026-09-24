#!/usr/bin/env bash
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CIPHER_DIR="${YTCIPHER_DIR:-${ROOT_DIR}/yt-cipher}"
PIDFILE="${ROOT_DIR}/.zenvibe-start.pid"

if [[ -f "${ROOT_DIR}/.env" ]]; then
    set -a
    source "${ROOT_DIR}/.env"
    set +a
fi

if [[ -f "${PIDFILE}" ]]; then
    old_pid="$(tr -d '[:space:]' < "${PIDFILE}")"
    if [[ "${old_pid}" =~ ^[0-9]+$ && "${old_pid}" != "$$" ]] && kill -0 "${old_pid}" 2>/dev/null; then
        pkill -P "${old_pid}" 2>/dev/null || true
        kill "${old_pid}" 2>/dev/null || true
        for _ in $(seq 1 20); do
            kill -0 "${old_pid}" 2>/dev/null || break
            sleep 0.1
        done
        kill -9 "${old_pid}" 2>/dev/null || true
    fi
fi
pkill -f 'java --enable-native-access=ALL-UNNAMED -jar bot.jar' 2>/dev/null || true
echo $$ > "${PIDFILE}"

export HOST="${YTCIPHER_HOST:-127.0.0.1}"
export PORT="${YTCIPHER_PORT:-8001}"
export OVERRIDE_SCRIPT_VARIANT="${OVERRIDE_SCRIPT_VARIANT:-IAS}"
export YTCIPHERSERVERURL="http://${HOST}:${PORT}"
export API_TOKEN="${YTCIPHERSERVERPASSWORD:-}"

cd -- "${CIPHER_DIR}"
deno run --allow-net --allow-read --allow-write --allow-env --env server.ts &
cipher_pid=$!

cleanup() {
    kill "${cipher_pid}" 2>/dev/null || true
    if [[ -f "${PIDFILE}" ]] && [[ "$(tr -d '[:space:]' < "${PIDFILE}")" == "$$" ]]; then
        rm -f "${PIDFILE}"
    fi
}

trap 'cleanup; exit 130' INT TERM
trap cleanup EXIT
sleep 2

cd -- "${ROOT_DIR}"
while true; do
    java --enable-native-access=ALL-UNNAMED -jar bot.jar
    sleep 2
done

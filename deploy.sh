#!/usr/bin/env bash
# Pull main from GitHub, compile shaded jar, put it in update/.

set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/ZeNyfh/Zenvibe.git}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/temurin-25-jdk-amd64}"
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

ensure_mvn() {
	if command -v mvn >/dev/null 2>&1; then
		MVN=(mvn)
		return
	fi
	local cache="${HOME}/.cache/zenvibe"
	local home="${cache}/apache-maven-3.9.9"
	if [[ ! -x "${home}/bin/mvn" ]]; then
		mkdir -p -- "${cache}"
		echo "downloading Maven 3.9.9 → ${home}"
		curl -fsSL "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" \
			| tar -xz -C "${cache}"
	fi
	MVN=("${home}/bin/mvn")
}

if [[ -f "${ROOT}/pom.xml" ]]; then
	SRC="${ROOT}"
	UPDATE_DIR="${1:-${ROOT}/update}"
	git -C "${SRC}" fetch origin main
	git -C "${SRC}" checkout main
	git -C "${SRC}" pull --ff-only origin main
else
	SRC="${ROOT}/Zenvibe"
	UPDATE_DIR="${ROOT}/update"
	if [[ ! -d "${SRC}/.git" ]]; then
		# empty placeholder dir is fine for clone
		rmdir --ignore-fail-on-non-empty "${SRC}" 2>/dev/null || true
		git clone "${REPO_URL}" "${SRC}"
	fi
	git -C "${SRC}" fetch origin main
	git -C "${SRC}" checkout main
	git -C "${SRC}" pull --ff-only origin main
fi

mkdir -p -- "${UPDATE_DIR}"
ensure_mvn
(cd -- "${SRC}" && "${MVN[@]}" -DskipTests package)

JAR="$(ls -1t "${SRC}"/target/bot-*.jar 2>/dev/null | grep -v '/original-' | head -1 || true)"
if [[ -z "${JAR}" ]]; then
	echo "no shaded jar in ${SRC}/target/ (expected bot-*.jar, not original-*)" >&2
	exit 1
fi

cp -f -- "${JAR}" "${UPDATE_DIR}/bot.jar.partial"
mv -f -- "${UPDATE_DIR}/bot.jar.partial" "${UPDATE_DIR}/bot.jar"
echo "wrote ${UPDATE_DIR}/bot.jar"

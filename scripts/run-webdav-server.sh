#!/bin/sh

populate_dir() {
	mkdir -p "$1"
	head -c 1000000 < /dev/urandom > "$1/1.bin"
	head -c 5000000 < /dev/urandom > "$1/2.bin"
	head -c 10000000 < /dev/urandom > "$1/3.bin"
}

BIN_DIR="$(mktemp -d)"
CONFIG_FILE="${BIN_DIR}/config.yml"
curl -L https://github.com/hacdias/webdav/releases/download/v4.1.0/darwin-amd64-webdav.tar.gz | tar xvz -C "${BIN_DIR}"

cat > "${CONFIG_FILE}" <<EOF
address: 0.0.0.0
port: 8000
auth: true

scope: .
modify: true
rules: []

users:
- username: test
  password: test
EOF

WEBDAV_DIR="$(mktemp -d)"
populate_dir "${WEBDAV_DIR}"
populate_dir "${WEBDAV_DIR}/a"
populate_dir "${WEBDAV_DIR}/a/b"
printf "WebDAV root: ${WEBDAV_DIR}\n"

cd "${WEBDAV_DIR}"
exec ${BIN_DIR}/webdav --config "${CONFIG_FILE}"

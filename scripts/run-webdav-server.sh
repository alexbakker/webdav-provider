#!/bin/sh

BIN_DIR="$(mktemp -d)"
CONFIG_FILE="${BIN_DIR}/config.yml"
curl -L https://github.com/hacdias/webdav/releases/download/v4.1.0/darwin-amd64-webdav.tar.gz | tar xvz -C "${BIN_DIR}"

cat > "${CONFIG_FILE}" <<EOF
address: 0.0.0.0
port: 8000
auth: true
users:
- username: test
  password: test
EOF

WEBDAV_DIR="$(mktemp -d)"
head -c 1000000 < /dev/urandom > "${WEBDAV_DIR}/1.bin"
head -c 5000000 < /dev/urandom > "${WEBDAV_DIR}/2.bin"
head -c 10000000 < /dev/urandom > "${WEBDAV_DIR}/3.bin"

cd "${WEBDAV_DIR}"
exec ${BIN_DIR}/webdav --config "${CONFIG_FILE}"

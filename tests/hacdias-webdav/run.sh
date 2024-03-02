#!/bin/sh

populate_dir() {
    mkdir -p "$1"
    head -c 1000000 < /dev/urandom > "$1/1.bin"
    head -c 5000000 < /dev/urandom > "$1/2.bin"
    head -c 10000000 < /dev/urandom > "$1/3.bin"
}

WEBDAV_DIR="$(mktemp -d)"
populate_dir "${WEBDAV_DIR}"
populate_dir "${WEBDAV_DIR}/a"
populate_dir "${WEBDAV_DIR}/a/a"
populate_dir "${WEBDAV_DIR}/a/b"
populate_dir "${WEBDAV_DIR}/a/c"
printf "WebDAV root: %s\n" "${WEBDAV_DIR}"

cd "${WEBDAV_DIR}" || false
exec webdav --config /etc/webdav/config.yml

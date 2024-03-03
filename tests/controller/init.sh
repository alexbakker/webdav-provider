#!/bin/sh

set -e

umask 002

populate_dir() {
    mkdir -p "$1"
    head -c 1000000 < /dev/urandom > "$1/1.bin"
    head -c 5000000 < /dev/urandom > "$1/2.bin"
    head -c 10000000 < /dev/urandom > "$1/3.bin"
}

WEBDAV_DIR="$1"
if [ ! -d "${WEBDAV_DIR}"]; then
    echo "No such directory: ${WEBDAV_DIR}"
    exit 1
fi

find "${WEBDAV_DIR}" -mindepth 1 -delete

populate_dir "${WEBDAV_DIR}"
populate_dir "${WEBDAV_DIR}/a"
populate_dir "${WEBDAV_DIR}/a/a"
populate_dir "${WEBDAV_DIR}/a/b"
populate_dir "${WEBDAV_DIR}/a/c"
printf "Init done: %s\n" "${WEBDAV_DIR}"

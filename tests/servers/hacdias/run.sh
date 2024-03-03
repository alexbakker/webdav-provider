#!/bin/sh

DATA_DIR="/dav/hacdias"
mkdir -p "${DATA_DIR}"
ln -s "${DATA_DIR}" /data
cd /data && exec webdav --config /etc/webdav/config.yml

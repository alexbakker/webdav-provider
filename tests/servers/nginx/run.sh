#!/bin/sh

DATA_DIR="/dav/nginx"
mkdir -p "${DATA_DIR}"
chown -R nginx:nginx "${DATA_DIR}"
chmod g+s "${DATA_DIR}"
ln -s "${DATA_DIR}" /data

exec nginx -g "daemon off;error_log /dev/stdout info;"

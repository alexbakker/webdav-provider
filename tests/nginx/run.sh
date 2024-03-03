#!/bin/sh

DATA_DIR="/dav/nginx"
mkdir -p "${DATA_DIR}"
chown -R www-data:www-data "${DATA_DIR}"
ln -s "${DATA_DIR}" /data

exec nginx -g "daemon off;"

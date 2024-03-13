#!/bin/sh

set -ex

DATA_DIR="/dav/nginx"
mkdir -p "${DATA_DIR}"
chown -R nginx:nginx "${DATA_DIR}"
chmod g+s "${DATA_DIR}"
ln -s "${DATA_DIR}" /data

# source: https://serverfault.com/a/638855
echo resolver $(awk 'BEGIN{ORS=" "} $1=="nameserver" {print $2}' /etc/resolv.conf) ";" > /etc/nginx/resolvers.conf

exec nginx -g "daemon off;error_log /dev/stdout info;"

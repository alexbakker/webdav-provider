#!/bin/sh

set -ex

DATA_DIR="/dav/nginx"
mkdir -p "${DATA_DIR}"
chown -R nginx:nginx "${DATA_DIR}"
chmod g+s "${DATA_DIR}"
ln -s "${DATA_DIR}" /data

resolver="resolver $(awk 'BEGIN{ORS=" "} $1=="nameserver" {print $2}' /etc/resolv.conf)"
sed "s/resolver;/${resolver};/" /etc/nginx/http.d/default.conf.tmpl > /etc/nginx/http.d/default.conf

exec nginx -g "daemon off;error_log /dev/stdout info;"

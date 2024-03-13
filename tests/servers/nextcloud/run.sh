#!/bin/sh

set -ex

DATA_DIR="/dav/nextcloud"
mkdir -p "${DATA_DIR}"
chown -R www-data:www-data "${DATA_DIR}"
chmod g+s "${DATA_DIR}"

exec /entrypoint.sh apache2-foreground

#!/bin/sh

set -ex

DATA_DIR="/dav/nextcloud"
mkdir -p "${DATA_DIR}"
chown -R www-data:www-data "${DATA_DIR}"
chmod g+s "${DATA_DIR}"

# Allow the controller to trigger a file rescan in the Nextcloud container
su - www-data -s /bin/bash -c "while true; do nc -l -p 1337 -c 'cd /var/www/html && php occ files:scan --all'; done" &

exec /entrypoint.sh apache2-foreground

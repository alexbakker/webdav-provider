#!/bin/sh

DATA_DIR="/dav/apache"
mkdir -p "${DATA_DIR}"
chown -R daemon:daemon "${DATA_DIR}"
chmod g+s "${DATA_DIR}"
ln -s "${DATA_DIR}" /data
exec httpd-foreground

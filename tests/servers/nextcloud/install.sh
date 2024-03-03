#!/bin/sh

# Skip the web-based installer
php occ maintenance:install "--admin-user=$NEXTCLOUD_ADMIN_USER" "--admin-pass=$NEXTCLOUD_ADMIN_PASSWORD"

# Trust the most common local origins
php occ config:system:set trusted_domains 0 --value=localhost
php occ config:system:set trusted_domains 1 --value=10.*
php occ config:system:set trusted_domains 2 --value=192.168.*
php occ config:system:set trusted_domains 3 --value=127.*

# Add a separate user for the tests
OC_PASS=ilovepasswordstrengthchecks php occ user:add --password-from-env test
mkdir -p /var/www/html/data/test
ln -s /dav/nextcloud /var/www/html/data/test/files

# Allow following symlinks to the data volume
php occ config:system:set localstorage.allowsymlinks --value=true

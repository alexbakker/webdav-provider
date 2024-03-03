#!/bin/sh

# Skip the web-based installer
php occ maintenance:install "--admin-user=$NEXTCLOUD_ADMIN_USER" "--admin-pass=$NEXTCLOUD_ADMIN_PASSWORD"

# Trust the most common local origins
php occ config:system:set trusted_domains 0 --value=localhost
php occ config:system:set trusted_domains 1 --value=10.*
php occ config:system:set trusted_domains 2 --value=192.168.*
php occ config:system:set trusted_domains 3 --value=127.*

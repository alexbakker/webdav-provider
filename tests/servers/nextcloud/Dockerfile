FROM nextcloud:28.0.2-apache
WORKDIR /var/www/html
COPY ./run.sh /
COPY ./install.sh /docker-entrypoint-hooks.d/before-starting/10-install.sh
CMD ["/run.sh"]

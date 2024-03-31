FROM nextcloud:28.0.2-apache
WORKDIR /var/www/html
RUN apt update && apt install -y netcat-traditional
COPY ./run.sh /
COPY ./install.sh /docker-entrypoint-hooks.d/before-starting/10-install.sh
CMD ["/run.sh"]

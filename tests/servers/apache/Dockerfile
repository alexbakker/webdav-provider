FROM httpd:2.4.43-alpine
WORKDIR /usr/local/apache2
RUN apk add curl
COPY ./run.sh /
COPY ./httpd-vhosts.conf conf/extra/httpd-vhosts.conf
RUN sed -i '/httpd-vhosts/s/^#//g' conf/httpd.conf \
    && sed -i '/mod_dav/s/^#//g' conf/httpd.conf
RUN mkdir -p /usr/local/apache2/var && chown daemon:daemon /usr/local/apache2/var
CMD ["/run.sh"]
EXPOSE 80

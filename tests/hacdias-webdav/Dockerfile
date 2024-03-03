FROM alpine:3
WORKDIR /
COPY ./run.sh /
COPY ./config.yml /etc/webdav/config.yml
RUN apk add curl
RUN curl -L "https://github.com/hacdias/webdav/releases/download/v4.2.0/linux-amd64-webdav.tar.gz" | tar -zxvf - webdav -C /usr/local/bin \
    && echo "a18066828732d027323146922b088d9a97ff97bb8e86a4c3f9309d17a0f4e057  /usr/local/bin/webdav" | sha256sum -c
CMD ["/run.sh"]
EXPOSE 8000

FROM alpine:3
COPY ./run.sh /
COPY ./default.conf.tmpl /etc/nginx/http.d/default.conf.tmpl
RUN apk add curl nginx nginx-mod-http-dav-ext
CMD ["./run.sh"]
EXPOSE 80

FROM alpine:3
COPY ./default.conf /etc/nginx/http.d/default.conf
RUN apk add curl nginx nginx-mod-http-dav-ext
RUN mkdir /data
CMD ["nginx", "-g", "daemon off;"]
EXPOSE 80

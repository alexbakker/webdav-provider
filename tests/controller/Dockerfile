FROM alpine:3
WORKDIR /
RUN apk add curl python3 py3-flask netcat-openbsd
COPY ./server.py /
COPY ./init.sh /
CMD ["flask", "--app", "server", "run", "-h", "0.0.0.0", "-p", "80"]
EXPOSE 80

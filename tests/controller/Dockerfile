FROM alpine:3
WORKDIR /
RUN apk add curl python3 py3-flask py3-gunicorn netcat-openbsd
COPY ./server.py /
COPY ./init.sh /
CMD ["gunicorn", "--access-logfile", "-", "--bind", "0.0.0.0:80", "server:app"]
EXPOSE 80

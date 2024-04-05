# WebDAV-Provider [![CI](https://github.com/alexbakker/webdav-provider/workflows/build/badge.svg)](https://github.com/alexbakker/webdav-provider/actions?query=workflow%3Abuild)

__WebDAV Provider__ is an Android app that can expose WebDAV storage to other
apps through Android's Storage Access Framework (SAF).



# Screenshots

[<img src="screenshots/1.png" width="200">](screenshots/1.png) [<img src="screenshots/2.png" width="200">](screenshots/2.png) [<img src="screenshots/3.png" width="200">](screenshots/3.png) [<img src="screenshots/4.png" width="200">](screenshots/4.png)

## Development

This project is automatically tested against a variety of different WebDAV servers. The tests run in an Android emulator and connect to the WebDAV servers running in separate containers on the host machine. 

To spin up the test environment:

```sh
docker compose --project-directory tests up -d --wait --force-recreate --build --renew-anon-volumes --remove-orphans
```

Assuming an Android emulator is running, use the following command to run the tests:

```sh
./gradlew connectedCheck
```

To shut the test environment down:

```sh
docker compose --project-directory tests down -v
```

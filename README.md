<img align="left" width="80" height="80" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"
alt="App icon">

# WebDAV Provider [![CI](https://github.com/alexbakker/webdav-provider/workflows/build/badge.svg)](https://github.com/alexbakker/webdav-provider/actions?query=workflow%3Abuild)

__WebDAV Provider__ is an Android app that can expose WebDAV through Android's
Storage Access Framework (SAF). This allows you to access your WebDAV storage
through Android's built-in file explorer, as well as other apps on your device.

[<img height=80 alt="Get it on Google Play"
src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
/>](https://play.google.com/store/apps/details?id=dev.rocli.android.webdav)

## Screenshots

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png"
width="200">](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) [<img
src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200">](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png"
width="200">](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) [<img
src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="200">](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png)

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

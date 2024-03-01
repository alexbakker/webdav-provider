package me.alexbakker.webdav.extensions

import java.nio.file.Path

fun String.ensureTrailingSlash(): String {
    return if (!this.endsWith("/")) {
        "$this/"
    } else {
        this
    }
}

fun Path.toDavPath(isDirectory: Boolean = false): String {
    return if (isDirectory) {
        toString().ensureTrailingSlash()
    } else {
        toString()
    }
}

package me.alexbakker.webdav.extensions

fun String.ensureTrailingSlash(): String {
    return if (!this.endsWith("/")) {
        "$this/"
    } else {
        this
    }
}

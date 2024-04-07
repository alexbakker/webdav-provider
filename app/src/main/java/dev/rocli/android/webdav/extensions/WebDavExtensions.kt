package dev.rocli.android.webdav.extensions

import okhttp3.HttpUrl
import java.nio.file.Path
import java.nio.file.Paths

fun String.ensureTrailingSlash(): String {
    return if (!this.endsWith("/")) {
        "$this/"
    } else {
        this
    }
}

fun Path.urlEncode(): Path {
    // Unfortunately okhttp doesn't expose its URL path segments escaper
    val url = HttpUrl.Builder()
        .scheme("http")
        .host("fake")
        .addPathSegments(toString())
        .build()
    return Paths.get(url.encodedPath)
}

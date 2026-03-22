package dev.rocli.android.webdav.extensions

import okhttp3.HttpUrl
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

fun String.ensureTrailingSlash(): String {
    return if (!this.endsWith("/")) {
        "$this/"
    } else {
        this
    }
}

fun String.urlDecodePath(): String {
    // URLDecoder is designed for form data and treats + as space.
    // For URL path decoding, + should be treated as a literal character.
    return URLDecoder.decode(replace("+", "%2B"), StandardCharsets.UTF_8.name())
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

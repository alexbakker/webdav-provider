package dev.rocli.android.webdav.provider

import dev.rocli.android.webdav.extensions.ensureTrailingSlash
import dev.rocli.android.webdav.extensions.urlEncode
import java.nio.file.Path

class WebDavPath(val path: Path, val isDirectory: Boolean) {
    override fun toString(): String {
        val encoded = path.urlEncode().toString()
        return if (isDirectory) {
            encoded.ensureTrailingSlash()
        } else {
            encoded
        }
    }
}

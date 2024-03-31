package me.alexbakker.webdav.provider

import me.alexbakker.webdav.extensions.ensureTrailingSlash
import java.nio.file.Path

class WebDavPath(val path: Path, val isDirectory: Boolean) {
    override fun toString(): String {
        return if (isDirectory) {
            path.toString().ensureTrailingSlash()
        } else {
            path.toString()
        }
    }
}

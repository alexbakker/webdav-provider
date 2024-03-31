package me.alexbakker.webdav.provider

import android.webkit.MimeTypeMap
import com.thegrizzlylabs.sardineandroid.model.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebDavFile(
    var path: Path,
    var isDirectory: Boolean = false,
    var contentType: String? = null,
    var isPending: Boolean = false
) {
    var parent: WebDavFile? = null
    var children: MutableList<WebDavFile> = ArrayList()
    val writable: Boolean = true

    var etag: String? = null
    var contentLength: Long? = null
    var quotaUsedBytes: Long? = null
    var quotaAvailableBytes: Long? = null

    var lastModified: Date? = null

    val name: String
        get() {
            if (path.fileName != null) {
                return path.fileName.toString()
            }

            return "/"
        }

    val davPath: WebDavPath
        get() = WebDavPath(path, isDirectory)

    val decodedName: String
        get() = URLDecoder.decode(name, StandardCharsets.UTF_8.name())

    constructor (res: Response, href: String = res.href)
            : this(Paths.get(href), res.propstat[0].prop.resourcetype.collection != null) {
        val prop = res.propstat[0].prop
        etag = prop.getetag
        contentType = parseContentType(name, prop.getcontenttype)
        contentLength = prop.getcontentlength?.toLongOrNull()
        quotaUsedBytes = prop.quotaUsedBytes?.content?.firstOrNull()?.toLongOrNull()
        quotaAvailableBytes = prop.quotaAvailableBytes?.content?.firstOrNull()?.toLongOrNull()
        lastModified = parseDate(prop.getlastmodified)
    }

    override fun toString(): String {
        return path.toString()
    }

    private fun parseDate(s: String?): Date? {
        if (s == null) {
            return s
        }

        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        return try {
            format.parse(s)
        } catch (e: ParseException) {
            null
        }
    }

    private fun parseContentType(fileName: String, contentType: String?): String {
        if (contentType != null) {
            return contentType
        }

        val ext = fileName.split(".").last()
        val res = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (res != null) {
            return res
        }

        return "application/octet-stream"
    }
}

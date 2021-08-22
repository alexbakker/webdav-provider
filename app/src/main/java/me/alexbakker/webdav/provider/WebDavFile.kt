package me.alexbakker.webdav.provider

import android.webkit.MimeTypeMap
import com.thegrizzlylabs.sardineandroid.model.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class WebDavFile(var path: String, var isDirectory: Boolean, var contentType: String? = null, var isGhost: Boolean = false) {
    var parent: WebDavFile? = null
    val children: MutableList<WebDavFile> = ArrayList()
    val writable: Boolean = true
    var isRoot = false

    var etag: String? = null
    var contentLength: Long? = null
    var quotaUsedBytes: Long? = null
    var quotaAvailableBytes: Long? = null

    var lastModified: Date? = null

    val name: String
        get() {
            val parts = path.split("/")
            for (part in parts.asReversed()) {
                if (part.isNotEmpty()) {
                    return part
                }
            }

            return "/"
        }

    val decodedName: String
        get() = URLDecoder.decode(name, StandardCharsets.UTF_8.toString())

    constructor (res: Response, href: String = res.href)
            : this(href, res.propstat[0].prop.resourcetype.collection != null) {
        val prop = res.propstat[0].prop
        etag = prop.getetag
        contentType = parseContentType(name, prop.getcontenttype)
        contentLength = prop.getcontentlength?.toLongOrNull()
        quotaUsedBytes = prop.quotaUsedBytes?.content?.firstOrNull()?.toLongOrNull()
        quotaAvailableBytes = prop.quotaAvailableBytes?.content?.firstOrNull()?.toLongOrNull()
        lastModified = parseDate(prop.getlastmodified)
    }

    fun findByPath(path: String): WebDavFile? {
        if (this.path == path) {
            return this
        }

        for (child in children) {
            if (child.path == path) {
                return child
            }

            val file = child.findByPath(path)
            if (file != null) {
                return file
            }
        }

        return null
    }

    fun replaceWith(file: WebDavFile) {
        if (parent != null) {
            val i = parent!!.children.indexOf(this)
            if (i != -1) {
                parent!!.children[i] = file
            } else {
                parent!!.children.add(file)
            }
            file.parent = parent
        }
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

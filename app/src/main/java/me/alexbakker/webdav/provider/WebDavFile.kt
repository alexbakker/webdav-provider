package me.alexbakker.webdav.provider

import com.thegrizzlylabs.sardineandroid.model.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class WebDavFile(var path: String, var isDirectory: Boolean, var contentType: String? = null) {
    var parent: WebDavFile? = null
    val children: MutableList<WebDavFile> = ArrayList()
    val writable: Boolean = true

    var contentLength: Int? = null
    var quotaUsedBytes: Int? = null
    var quotaAvailableBytes: Int? = null

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
        contentType = prop.getcontenttype ?: "application/octet-stream"
        contentLength = prop.getcontentlength?.toIntOrNull()
        quotaUsedBytes = prop.quotaUsedBytes?.content?.firstOrNull()?.toIntOrNull()
        quotaAvailableBytes = prop.quotaAvailableBytes?.content?.firstOrNull()?.toIntOrNull()
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
        }
    }

    private fun parseDate(s: String): Date? {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        return try {
            format.parse(s)
        } catch (e: ParseException) {
            null
        }
    }
}

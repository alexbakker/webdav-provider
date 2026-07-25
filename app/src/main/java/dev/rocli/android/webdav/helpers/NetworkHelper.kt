package dev.rocli.android.webdav.helpers

import java.net.InetAddress

object NetworkHelper {
    private fun InetAddress.isLocal(): Boolean =
        isSiteLocalAddress || isLinkLocalAddress || isLoopbackAddress

    private fun parseIpLiteral(host: String): InetAddress? {
        val stripped = if (host.startsWith('[') && host.endsWith(']'))
            host.substring(1, host.length - 1) else host
        return try {
            val addr = InetAddress.getByName(stripped)
            addr.takeIf { it.hostAddress == stripped }
        } catch (_: Exception) {
            null
        }
    }

    fun isLocalNetworkHost(host: String): Boolean {
        if (host.endsWith(".local", ignoreCase = true)) {
            return true
        }
        if (!host.contains('.') && !host.contains(':')) {
            return true
        }
        val addr = parseIpLiteral(host)
        if (addr != null) {
            return addr.isLocal()
        }
        return try {
            val addrs = InetAddress.getAllByName(host)
            addrs.isNotEmpty() && addrs.all { it.isLocal() }
        } catch (_: Exception) {
            false
        }
    }
}

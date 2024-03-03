package me.alexbakker.webdav.provider

import android.content.Context
import me.alexbakker.webdav.data.Account

class WebDavClientManager(private val context: Context) {
    private val lock = Any()
    private val clients: MutableMap<Long, WebDavClient> = HashMap()

    fun get(account: Account): WebDavClient {
        synchronized(lock) {
            var client = clients[account.id]
            if (client != null) {
                return client
            }

            val creds = if (account.username != null && account.password != null) {
                Pair(account.username!!, account.password!!)
            } else {
                null
            }

            client = WebDavClient(
                context,
                account.baseUrl,
                creds,
                account.clientCert,
                account.verifyCerts,
                noHttp2 = account.protocol != Account.Protocol.AUTO
            )

            clients[account.id] = client
            return client
        }
    }

    fun delete(account: Account) {
        synchronized(lock) {
            clients.remove(account.id)
        }
    }
}

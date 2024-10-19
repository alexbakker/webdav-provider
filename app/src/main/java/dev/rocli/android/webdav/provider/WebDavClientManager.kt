package dev.rocli.android.webdav.provider

import android.content.Context
import dev.rocli.android.webdav.data.Account

class WebDavClientManager(private val context: Context) {
    private val lock = Any()
    private val clients: MutableMap<Long, WebDavClient> = HashMap()

    fun get(account: Account): WebDavClient {
        if (account.hasError) {
            throw IllegalStateException("Attempt to use WebDAV account that has an error")
        }

        synchronized(lock) {
            var client = clients[account.id]
            if (client != null) {
                return client
            }

            val creds = if (account.authType != Account.AuthType.NONE) {
                account.username?.value?.let { username ->
                    account.password?.value?.let { password ->
                        when (account.authType) {
                            Account.AuthType.BASIC -> {
                                WebDavCredentials(WebDavCredentials.AuthType.BASIC, username, password)
                            }
                            Account.AuthType.DIGEST -> {
                                WebDavCredentials(WebDavCredentials.AuthType.DIGEST, username, password)
                            }
                            else -> {
                                null
                            }
                        }
                    }
                }
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

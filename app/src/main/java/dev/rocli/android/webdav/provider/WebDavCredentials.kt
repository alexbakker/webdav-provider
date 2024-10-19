package dev.rocli.android.webdav.provider

data class WebDavCredentials(
    val type: AuthType,
    val username: String,
    val password: String
) {
    enum class AuthType {
        BASIC,
        DIGEST
    }
}

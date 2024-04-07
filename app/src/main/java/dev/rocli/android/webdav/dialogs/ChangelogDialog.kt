package dev.rocli.android.webdav.dialogs

import android.content.Context
import dev.rocli.android.webdav.R

class ChangelogDialog : SimpleWebViewDialog(R.string.about_changelog) {
    override fun getContent(context: Context): String {
        val html = readAssetAsString(context, "changelog.html")
        return String.format(html, backgroundColor, textColor)
    }

    companion object {
        fun create(): ChangelogDialog {
            return ChangelogDialog()
        }
    }
}

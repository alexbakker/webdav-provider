package dev.rocli.android.webdav.dialogs

import android.content.Context
import dev.rocli.android.webdav.R

class LicenseDialog : SimpleWebViewDialog(R.string.about_license) {
    override fun getContent(context: Context): String {
        val license = readAssetAsString(context, "LICENSE")
        val html = readAssetAsString(context, "license.html")
        return String.format(html, license, backgroundColor, textColor)
    }

    companion object {
        fun create(): LicenseDialog {
            return LicenseDialog()
        }
    }
}

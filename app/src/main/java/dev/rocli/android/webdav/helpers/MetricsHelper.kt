package dev.rocli.android.webdav.helpers

import android.content.Context
import android.util.DisplayMetrics

object MetricsHelper {
    fun convertDpToPixels(context: Context, dp: Float): Int {
        return (dp * (context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }
}

package me.alexbakker.webdav.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
open class LiveDataEvent<out T : Parcelable>(private val content: T) : Parcelable {
    var handled = false
        private set

    fun getContent(): T? {
        return if (handled) {
            null
        } else {
            handled = true
            content
        }
    }
}

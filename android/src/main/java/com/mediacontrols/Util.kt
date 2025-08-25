package com.mediacontrols

import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle

inline fun <reified T> Intent.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

inline fun <reified T> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

inline fun <reified T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
    val fromIndex = page * pageSize
    if (fromIndex >= this.size) {
        return emptyList()
    }
    val toIndex = minOf(fromIndex + pageSize, this.size)
    return this.subList(fromIndex, toIndex)
}
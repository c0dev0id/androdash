package com.androdash

import android.content.Context

class HiddenAppsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("androdash_prefs", Context.MODE_PRIVATE)
    private val key = "hidden_apps"

    fun getHiddenPackages(): Set<String> =
        prefs.getStringSet(key, emptySet()) ?: emptySet()

    fun setHidden(packageName: String) {
        val current = getHiddenPackages().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(key, current).apply()
    }

    fun setVisible(packageName: String) {
        val current = getHiddenPackages().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(key, current).apply()
    }
}

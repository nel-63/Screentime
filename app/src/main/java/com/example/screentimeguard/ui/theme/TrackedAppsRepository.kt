package com.example.screentimeguard

import android.content.Context

object TrackedAppsRepository {

    private const val PREFS_NAME = "tracked_apps_prefs"
    private const val KEY_TRACKED = "tracked_packages"

    fun saveTrackedApps(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_TRACKED, packages)
            .apply()
    }

    fun getTrackedApps(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_TRACKED, emptySet()) ?: emptySet()
    }
}
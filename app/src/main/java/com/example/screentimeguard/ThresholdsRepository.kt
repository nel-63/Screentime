package com.example.screentimeguard

import android.content.Context

object ThresholdsRepository {

    private const val PREFS_NAME = "thresholds_prefs"

    // Seuil en minutes pour une app donnée. -1 = pas de seuil défini.
    fun setThresholdMinutes(context: Context, packageName: String, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("threshold_$packageName", minutes)
            .apply()
    }

    fun getThresholdMinutes(context: Context, packageName: String): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("threshold_$packageName", -1)
    }

    // Pour éviter de spammer une notification toutes les 2 secondes une fois le seuil dépassé :
    // on retient la date du dernier avertissement envoyé pour cette app.
    fun getLastNotifiedDate(context: Context, packageName: String): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("notified_$packageName", null)
    }

    fun setLastNotifiedDate(context: Context, packageName: String, date: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("notified_$packageName", date)
            .apply()
    }
}
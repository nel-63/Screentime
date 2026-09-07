package com.example.screentimeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.provider.Settings

class AppMonitorService : LifecycleService() {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var database: AppDatabase
    private val handler = Handler(Looper.getMainLooper())
    private var lastQueryTime = System.currentTimeMillis()
    private val pollingInterval = 2000L

    // overlayActiveFor a été déplacé dans OverlayState (voir OverlayState.kt) :
    // seule l'activité overlay elle-même peut désormais le remettre à null,
    // via onDestroy(), au lieu de le déduire d'évènements UsageEvents ambigus.

    private var lastForegroundApp: String? = null

    // Verrou synchrone partagé : empêche de traiter deux fois la même app en même
    // temps, que ce soit déclenché par l'évènement MOVE_TO_FOREGROUND ou par la
    // vérification périodique faite à chaque cycle de polling (voir triggerEvaluation).
    private val processingApp = mutableSetOf<String>()

    private val pollingRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, pollingInterval)
        }
    }

    companion object {
        const val CHANNEL_ID = "screentimeguard_monitor"
        const val ALERT_CHANNEL_ID = "screentimeguard_alerts"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        database = AppDatabase.getDatabase(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(pollingRunnable)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Surveillance des applications",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(monitorChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Alertes de limite de temps",
                NotificationManager.IMPORTANCE_DEFAULT  // avec son, contrairement au canal silencieux
            )
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenTimeGuard actif")
            .setContentText("Surveillance en cours...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun getTrackedPackages(): Set<String> {
        return TrackedAppsRepository.getTrackedApps(applicationContext)
    }

    private fun checkForegroundApp() {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastQueryTime, now)
        val event = UsageEvents.Event()
        val trackedPackages = getTrackedPackages()
        val ownPackage = applicationContext.packageName

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val packageName = event.packageName
            val timestamp = event.timeStamp

            // [DIAGNOSTIC] log de TOUT évènement brut, y compris notre propre app,
            // pour voir la vraie séquence envoyée par Android.
            val eventTypeName = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> "FOREGROUND"
                UsageEvents.Event.MOVE_TO_BACKGROUND -> "BACKGROUND"
                UsageEvents.Event.ACTIVITY_PAUSED -> "ACTIVITY_PAUSED"
                UsageEvents.Event.ACTIVITY_RESUMED -> "ACTIVITY_RESUMED"
                UsageEvents.Event.ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
                else -> "OTHER(${event.eventType})"
            }
            Log.d(
                "AppMonitorRaw",
                "[$eventTypeName] pkg=$packageName ts=$timestamp overlayActiveFor=${OverlayState.activeFor} lastForegroundApp=$lastForegroundApp"
            )

            // Ignore complètement les transitions liées à notre propre app
            // (InterstitialActivity / BlockActivity qui s'ouvrent/ferment)
            if (packageName == ownPackage) {
                continue
            }

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                // Si l'app qui revient au premier plan est celle pour laquelle
                // notre overlay est actif, on ignore l'évènement SANS remettre
                // overlayActiveFor à null ici : ça peut être un simple blip
                // transitoire pendant l'ouverture de notre overlay, pas un vrai
                // retour de l'utilisateur. Seul OverlayState (mis à jour par
                // l'activité overlay dans onDestroy) fait foi.
                if (packageName == OverlayState.activeFor) {
                    Log.d("AppMonitorRaw", "  -> évènement ignoré, overlay actif pour $packageName (pas de reset ici)")
                    lastForegroundApp = packageName
                    continue  // on ne redéclenche ni l'interstitiel ni un nouveau départ de session
                }
                lastForegroundApp = packageName
            }

            if (packageName !in trackedPackages) continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    onAppEnteredForeground(packageName, timestamp)
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // Si c'est notre propre overlay qui prend le dessus, ce n'est pas une vraie sortie
                    if (packageName != OverlayState.activeFor) {
                        onAppLeftForeground(packageName, timestamp)
                    }
                }
            }
        }

        lastQueryTime = now

        lastForegroundApp?.let { currentApp ->
            // Vérification PÉRIODIQUE (à chaque cycle de polling, ~2s), en plus
            // de la vérification déclenchée par l'évènement MOVE_TO_FOREGROUND.
            // Ça comble les cas où l'app arrive au premier plan par un chemin
            // qui ne déclenche pas proprement onAppEnteredForeground (ex : bouton
            // "applications récentes" / multitâche) : même si l'entrée initiale
            // est ratée, ce check la rattrape au plus tard au polling suivant.
            if (currentApp in trackedPackages) {
                triggerEvaluation(currentApp, System.currentTimeMillis())
            }
        }
    }

    private fun triggerEvaluation(packageName: String, now: Long) {
        if (packageName in processingApp) return
        processingApp.add(packageName)
        lifecycleScope.launch {
            try {
                evaluateAndAct(packageName, now)
            } finally {
                processingApp.remove(packageName)
            }
        }
    }

    private val sessionStartTimes = mutableMapOf<String, Long>()

    private fun onAppLeftForeground(packageName: String, timestamp: Long) {
        val startTime = sessionStartTimes[packageName] ?: return
        val duration = timestamp - startTime

        Log.d("AppMonitor", "Fin session : $packageName (durée: ${duration}ms)")

        lifecycleScope.launch {
            try {
                database.appSessionDao().insertSession(
                    AppSession(
                        packageName = packageName,
                        startTime = startTime,
                        endTime = timestamp,
                        durationMs = duration
                    )
                )
                checkThresholdExceeded(packageName)
            } catch (e: Exception) {
                Log.e("AppMonitor", "Échec de l'insertion en base", e)
            }
        }

        sessionStartTimes.remove(packageName)
    }

    private suspend fun checkThresholdExceeded(packageName: String) {
        val thresholdMinutes = ThresholdsRepository.getThresholdMinutes(applicationContext, packageName)
        if (thresholdMinutes <= 0) return  // pas de seuil défini pour cette app

        val totalTodayMs = database.appSessionDao()
            .getTotalDurationSince(packageName, getStartOfTodayMillis()) ?: 0L
        val totalTodayMinutes = totalTodayMs / 1000 / 60

        if (totalTodayMinutes >= thresholdMinutes) {
            val today = getTodayDateString()
            val lastNotified = ThresholdsRepository.getLastNotifiedDate(applicationContext, packageName)

            // On n'envoie qu'une seule notification par app par jour
            if (lastNotified != today) {
                sendThresholdNotification(packageName, thresholdMinutes.toLong())
                ThresholdsRepository.setLastNotifiedDate(applicationContext, packageName, today)
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun sendThresholdNotification(packageName: String, thresholdMinutes: Long) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("AppMonitor", "Permission POST_NOTIFICATIONS non accordée, notification annulée")
            return
        }

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Limite atteinte")
            .setContentText("Vous avez dépassé $thresholdMinutes min sur $appName aujourd'hui")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(packageName.hashCode(), notification)
    }

    private fun onAppEnteredForeground(packageName: String, timestamp: Long) {
        sessionStartTimes[packageName] = timestamp
        Log.d("AppMonitor", "Début session : $packageName à $timestamp")
        triggerEvaluation(packageName, timestamp)
    }

    private suspend fun evaluateAndAct(
        packageName: String,
        now: Long
    ) {

        Log.d(
            "AppMonitorRaw",
            "evaluateAndAct($packageName) " +
                    "activeFor=${OverlayState.activeFor} " +
                    "completedFor=${OverlayState.completedFor}"
        )

        if (packageName == applicationContext.packageName) {
            return
        }

        // 1. Notre écran est réellement visible.
        if (OverlayState.activeFor == packageName) {
            return
        }

        // 2. L'utilisateur a réellement validé.
        if (OverlayState.completedFor == packageName) {
            return
        }

        // 3. Ton contrôle de limite existant.
        if (isThresholdExceeded(packageName, now)) {

            launchBlockScreen(packageName)

        } else {

            launchInterstitialScreen(packageName)
        }
    }


    private suspend fun isThresholdExceeded(packageName: String, now: Long): Boolean {
        val thresholdMinutes = ThresholdsRepository.getThresholdMinutes(applicationContext, packageName)
        if (thresholdMinutes <= 0) return false

        val totalStoredMs = database.appSessionDao()
            .getTotalDurationSince(packageName, getStartOfTodayMillis()) ?: 0L

        val currentSessionStart = sessionStartTimes[packageName]
        val ongoingSessionMs = if (currentSessionStart != null) now - currentSessionStart else 0L

        val totalTodayMinutes = (totalStoredMs + ongoingSessionMs) / 1000 / 60
        return totalTodayMinutes >= thresholdMinutes
    }

    private fun launchInterstitialScreen(packageName: String) {
        Log.d("AppMonitorRaw", "launchInterstitialScreen($packageName) — overlayActiveFor: ${OverlayState.activeFor} -> $packageName")

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val intent = Intent(this, InterstitialActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            putExtra("appName", appName)
            putExtra("packageName", packageName)
        }
        startActivity(intent)
    }

    private fun launchBlockScreen(packageName: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("AppMonitor", "Permission overlay non accordée, blocage impossible")
            return
        }

        Log.d("AppMonitorRaw", "launchBlockScreen($packageName) — overlayActiveFor: ${OverlayState.activeFor} -> $packageName")

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            putExtra("appName", appName)
            putExtra("packageName", packageName)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollingRunnable)
    }
}
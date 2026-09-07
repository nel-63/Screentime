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
                        onAppMayHaveLeftForeground(packageName, timestamp)
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

        // Finalise toute session dont la sortie du premier plan remonte à
        // plus de sessionMergeWindowMs sans que l'app ne soit revenue au
        // premier plan entre-temps : c'est seulement à ce moment-là qu'on
        // est sûrs qu'il ne s'agissait pas d'un simple aller-retour rapide.
        finalizeExpiredSessions(now)
    }

    private fun finalizeExpiredSessions(now: Long) {
        if (pendingSessionEnd.isEmpty()) return
        val expired = pendingSessionEnd.filter { (_, leftAt) -> now - leftAt >= sessionMergeWindowMs }.keys.toList()
        for (packageName in expired) {
            val leftAt = pendingSessionEnd.remove(packageName) ?: continue
            finalizeSession(packageName, leftAt)
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

    // Sessions "en attente de confirmation de fin" : quand une app suivie
    // quitte le premier plan, on ne clôture pas tout de suite la session en
    // base. On attend de voir si l'utilisateur y retourne dans la fenêtre de
    // fusion ci-dessous — un aller-retour rapide (notification, bascule
    // brève vers une autre app, etc.) compte alors comme LA MÊME session
    // logique plutôt que deux sessions distinctes. La session n'est
    // réellement finalisée (écrite en base, seuil vérifié, completedFor
    // réinitialisé) que si l'app reste absente au moins sessionMergeWindowMs.
    private val pendingSessionEnd = mutableMapOf<String, Long>()
    private val sessionMergeWindowMs = 35_000L

    private fun onAppMayHaveLeftForeground(packageName: String, timestamp: Long) {
        // On ne marque une sortie candidate que s'il y a bien une session en
        // cours pour ce package ; sinon rien à fusionner ni à finaliser.
        if (sessionStartTimes[packageName] == null) return
        pendingSessionEnd[packageName] = timestamp
        Log.d(
            "AppMonitor",
            "Sortie potentielle : $packageName à $timestamp (confirmation dans ${sessionMergeWindowMs}ms si pas de retour)"
        )
    }

    private fun finalizeSession(packageName: String, endTimestamp: Long) {
        val startTime = sessionStartTimes[packageName] ?: return
        val duration = endTimestamp - startTime

        Log.d("AppMonitor", "Fin session : $packageName (durée: ${duration}ms)")

        // La validation de l'interstitiel ne doit valoir que pour la session
        // en cours. Sans ça, completedFor n'est jamais remis à null ailleurs
        // dans le code (voir OverlayState.kt) et reste vrai pour le reste de
        // la vie du process : une fois validé, l'app ne redemanderait plus
        // jamais, ce qui n'a pas de sens pour une app de gestion de temps
        // d'écran. On la reset donc ici, au moment où l'app surveillée quitte
        // *réellement* le premier plan (fin de session confirmée, après la
        // fenêtre de fusion de sessionMergeWindowMs — pas à chaque blip).
        if (OverlayState.completedFor == packageName) {
            OverlayState.completedFor = null
            Log.d("AppMonitorRaw", "completedFor réinitialisé pour $packageName (fin de session)")
        }

        lifecycleScope.launch {
            try {
                database.appSessionDao().insertSession(
                    AppSession(
                        packageName = packageName,
                        startTime = startTime,
                        endTime = endTimestamp,
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
        val pendingLeftAt = pendingSessionEnd[packageName]

        if (pendingLeftAt != null && (timestamp - pendingLeftAt) < sessionMergeWindowMs) {
            // Retour à moins de 35s : on considère qu'il s'agit de la même
            // session logique. On annule la sortie candidate SANS toucher à
            // sessionStartTimes[packageName], pour que la durée totale
            // continue de courir depuis le tout premier démarrage réel de
            // la session (le petit aller-retour est donc inclus dans la
            // durée totale, ce qui est le compromis attendu de la fusion).
            pendingSessionEnd.remove(packageName)
            Log.d(
                "AppMonitor",
                "Reprise de session : $packageName (retour après ${timestamp - pendingLeftAt}ms, fusionné avec la session en cours)"
            )
        } else {
            sessionStartTimes[packageName] = timestamp
            Log.d("AppMonitor", "Début session : $packageName à $timestamp")
        }

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

        // On marque l'overlay comme actif de façon SYNCHRONE, avant même
        // d'appeler startActivity(). Avant ce correctif, OverlayState.activeFor
        // n'était mis à jour que dans InterstitialActivity.onStart(), donc de
        // façon asynchrone : entre cet appel et le vrai onStart(), il existait
        // une fenêtre pendant laquelle evaluateAndAct() voyait encore
        // activeFor == null et pouvait redéclencher un second lancement pour
        // le même package (cycle de polling suivant, ou double évènement
        // MOVE_TO_FOREGROUND). C'était la cause du flicker observé dans les
        // logs (ACTIVITY_STOPPED en rafale + relances répétées).
        OverlayState.activeFor = packageName

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val intent = Intent(this, InterstitialActivity::class.java).apply {
            // FLAG_ACTIVITY_CLEAR_TASK a été retiré : combiné à NEW_TASK, il
            // détruisait systématiquement la task existante (donc l'instance
            // d'InterstitialActivity déjà affichée) avant d'en recréer une
            // nouvelle à chaque appel, même redondant. FLAG_ACTIVITY_MULTIPLE_TASK
            // est retiré aussi : on ne veut jamais plusieurs instances de
            // l'interstitiel empilées en parallèle pour le même package.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

        // Même correctif que launchInterstitialScreen : marquage synchrone
        // avant startActivity(), pour éviter la même fenêtre de course.
        OverlayState.activeFor = packageName

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val intent = Intent(this, BlockActivity::class.java).apply {
            // Mêmes raisons que pour l'interstitiel : plus de CLEAR_TASK /
            // MULTIPLE_TASK, pour ne pas détruire/recréer l'activité de
            // blocage à chaque appel redondant.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
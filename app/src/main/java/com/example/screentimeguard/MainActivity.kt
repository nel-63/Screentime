package com.example.screentimeguard

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.screentimeguard.ui.theme.ScreenTimeGuardTheme

class MainActivity : ComponentActivity() {

    private var usageAccessGranted by mutableStateOf(false)
    private var overlayPermissionGranted by mutableStateOf(false)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* résultat ignoré pour l'instant */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usageAccessGranted = hasUsageStatsPermission()
        overlayPermissionGranted = hasOverlayPermission()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ScreenTimeGuardTheme {
                var currentScreen by remember { mutableStateOf("main") }

                when (currentScreen) {
                    "selection" -> AppSelectionScreen(
                        onDone = { currentScreen = "main" }
                    )
                    "stats" -> StatsScreen(
                        onBack = { currentScreen = "main" }
                    )
                    "thresholds" -> ThresholdSettingsScreen(
                        onBack = { currentScreen = "main" }
                    )
                    else -> MainScreen(
                        usageAccessGranted = usageAccessGranted,
                        overlayPermissionGranted = overlayPermissionGranted,
                        onRequestPermission = { openUsageAccessSettings() },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onStartService = { startMonitorService() },
                        onSelectApps = { currentScreen = "selection" },
                        onShowStats = { currentScreen = "stats" },
                        onSetThresholds = { currentScreen = "thresholds" }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccessGranted = hasUsageStatsPermission()
        overlayPermissionGranted = hasOverlayPermission()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startMonitorService() {
        val intent = Intent(this, AppMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun MainScreen(
    usageAccessGranted: Boolean,
    overlayPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartService: () -> Unit,
    onSelectApps: () -> Unit,
    onShowStats: () -> Unit,
    onSetThresholds: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Screen Time Guard",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = if (usageAccessGranted) {
                "Accès aux données d'utilisation : AUTORISÉ"
            } else {
                "Accès aux données d'utilisation : NON AUTORISÉ"
            },
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = if (overlayPermissionGranted) {
                "Permission d'affichage par-dessus : AUTORISÉE"
            } else {
                "Permission d'affichage par-dessus : NON AUTORISÉE"
            },
            modifier = Modifier.padding(top = 8.dp)
        )

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text("Autoriser l'accès aux données")
        }

        Button(
            onClick = onRequestOverlayPermission,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Autoriser l'affichage par-dessus")
        }

        Button(
            onClick = onStartService,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Démarrer la surveillance")
        }

        Button(
            onClick = onSelectApps,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Choisir les applications à surveiller")
        }

        Button(
            onClick = onShowStats,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Voir les statistiques")
        }

        Button(
            onClick = onSetThresholds,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Définir les limites")
        }
    }
}
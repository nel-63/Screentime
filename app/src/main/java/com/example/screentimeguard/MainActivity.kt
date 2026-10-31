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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                var currentScreen by remember { mutableStateOf("home") }

                when (currentScreen) {
                    "selection" -> AppSelectionScreen(
                        onDone = { currentScreen = "home" }
                    )
                    "stats" -> StatsScreen(
                        onBack = { currentScreen = "home" }
                    )
                    "thresholds" -> ThresholdSettingsScreen(
                        onBack = { currentScreen = "home" }
                    )
                    "settings" -> SettingsScreen(
                        usageAccessGranted = usageAccessGranted,
                        overlayPermissionGranted = overlayPermissionGranted,
                        onRequestPermission = { openUsageAccessSettings() },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onStartService = { startMonitorService() },
                        onBack = { currentScreen = "home" }
                    )
                    else -> LockerHomeScreen(
                        onSettings = { currentScreen = "settings" },
                        onStats = { currentScreen = "stats" },
                        onLimits = { currentScreen = "thresholds" },
                        onApps = { currentScreen = "selection" }
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
fun SettingsScreen(
    usageAccessGranted: Boolean,
    overlayPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartService: () -> Unit,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "Paramètres",
        subtitle = "Autorisations et surveillance",
        onBack = onBack
    ) {
        PermissionCard(
            title = "Accès aux données d'utilisation",
            granted = usageAccessGranted,
            buttonLabel = "Autoriser l'accès aux données",
            onClick = onRequestPermission
        )

        PermissionCard(
            title = "Affichage par-dessus les autres apps",
            granted = overlayPermissionGranted,
            buttonLabel = "Autoriser l'affichage par-dessus",
            onClick = onRequestOverlayPermission
        )

        Spacer(modifier = Modifier.height(12.dp))

        RedButton(
            text = "Démarrer la surveillance",
            onClick = onStartService
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    AppCard(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(granted = granted)
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Rouge tant que la permission manque, gris une fois accordée.
            if (granted) {
                GreyButton(text = buttonLabel, onClick = onClick)
            } else {
                RedButton(text = buttonLabel, onClick = onClick)
            }
        }
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    Text(
        text = if (granted) "AUTORISÉ" else "NON AUTORISÉ",
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (granted) AppColors.CardAlt else AppColors.Red)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

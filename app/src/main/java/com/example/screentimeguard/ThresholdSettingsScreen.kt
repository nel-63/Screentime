package com.example.screentimeguard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThresholdSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val trackedPackages = remember { TrackedAppsRepository.getTrackedApps(context).toList() }

    // État local : packageName -> texte saisi (en minutes)
    val thresholds = remember {
        mutableStateMapOf<String, String>().apply {
            trackedPackages.forEach { pkg ->
                val current = ThresholdsRepository.getThresholdMinutes(context, pkg)
                put(pkg, if (current > 0) current.toString() else "")
            }
        }
    }

    ScreenScaffold(
        title = "Limites",
        subtitle = "Définir les limites (en minutes/jour)",
        onBack = onBack
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(trackedPackages, key = { it }) { pkg ->
                val appName = try {
                    context.packageManager
                        .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                        .toString()
                } catch (e: Exception) {
                    pkg
                }

                AppCard(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        AppIcon(packageName = pkg, appName = appName, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = appName,
                            color = AppColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = thresholds[pkg] ?: "",
                            onValueChange = { thresholds[pkg] = it.filter { c -> c.isDigit() } },
                            label = { Text("min") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary,
                                focusedBorderColor = AppColors.Red,
                                unfocusedBorderColor = AppColors.Border,
                                focusedLabelColor = AppColors.Red,
                                unfocusedLabelColor = AppColors.TextSecondary,
                                cursorColor = AppColors.Red
                            )
                        )
                    }
                }
            }
        }

        RedButton(
            text = "Enregistrer les limites",
            onClick = {
                thresholds.forEach { (pkg, value) ->
                    val minutes = value.toIntOrNull() ?: 0
                    ThresholdsRepository.setThresholdMinutes(context, pkg, minutes)
                }
                onBack()
            },
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

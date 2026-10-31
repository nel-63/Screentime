package com.example.screentimeguard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppSelectionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selected by remember { mutableStateOf(TrackedAppsRepository.getTrackedApps(context)) }

    LaunchedEffect(Unit) {
        apps = AppListHelper.getInstalledApps(context)
    }

    ScreenScaffold(
        title = "Applications",
        subtitle = "Choisissez les applications à surveiller",
        onBack = onDone
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                val isSelected = selected.contains(app.packageName)

                AppCard(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                selected = if (isSelected) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            }
                            .padding(12.dp)
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            appName = app.appName,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = app.appName,
                            color = AppColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppColors.Red,
                                uncheckedColor = AppColors.TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        RedButton(
            text = "Enregistrer la sélection",
            onClick = {
                TrackedAppsRepository.saveTrackedApps(context, selected)
                onDone()
            },
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

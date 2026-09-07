package com.example.screentimeguard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppSelectionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selected by remember { mutableStateOf(TrackedAppsRepository.getTrackedApps(context)) }

    LaunchedEffect(Unit) {
        apps = AppListHelper.getInstalledApps(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Choisissez les applications à surveiller",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Image(
                        bitmap = app.icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = app.appName,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = selected.contains(app.packageName),
                        onCheckedChange = { checked ->
                            selected = if (checked) {
                                selected + app.packageName
                            } else {
                                selected - app.packageName
                            }
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                TrackedAppsRepository.saveTrackedApps(context, selected)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Enregistrer la sélection")
        }
    }
}
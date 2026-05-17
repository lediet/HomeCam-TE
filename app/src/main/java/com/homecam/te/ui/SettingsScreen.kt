package com.homecam.te.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen for alert configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AlertSettings,
    onUpdateSettings: (AlertSettings) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Master switch
            SettingsSection(title = "报警总开关") {
                SwitchRow(
                    label = "启用报警",
                    checked = settings.enabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) }
                )
            }

            HorizontalDivider()

            // Event type switches
            SettingsSection(title = "按事件类型") {
                SwitchRow(
                    label = "进入报警",
                    checked = settings.enterAlert,
                    onCheckedChange = { onUpdateSettings(settings.copy(enterAlert = it)) },
                    enabled = settings.enabled
                )
                SwitchRow(
                    label = "离开报警",
                    checked = settings.leaveAlert,
                    onCheckedChange = { onUpdateSettings(settings.copy(leaveAlert = it)) },
                    enabled = settings.enabled
                )
                SwitchRow(
                    label = "哭声报警",
                    checked = settings.cryAlert,
                    onCheckedChange = { onUpdateSettings(settings.copy(cryAlert = it)) },
                    enabled = settings.enabled
                )
                SwitchRow(
                    label = "睡眠报警",
                    checked = settings.sleepAlert,
                    onCheckedChange = { onUpdateSettings(settings.copy(sleepAlert = it)) },
                    enabled = settings.enabled
                )
            }

            HorizontalDivider()

            // Alert mode
            SettingsSection(title = "报警方式") {
                SwitchRow(
                    label = "声音报警",
                    checked = settings.voice,
                    onCheckedChange = { onUpdateSettings(settings.copy(voice = it)) },
                    enabled = settings.enabled
                )
                SwitchRow(
                    label = "振动",
                    checked = settings.vibrate,
                    onCheckedChange = { onUpdateSettings(settings.copy(vibrate = it)) },
                    enabled = settings.enabled
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked && enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

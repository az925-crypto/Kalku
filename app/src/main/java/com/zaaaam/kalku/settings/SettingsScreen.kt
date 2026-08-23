package com.zaaaam.kalku.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.ThemeMode
import com.zaaaam.kalku.fs.VaultPaths
import com.zaaaam.kalku.security.LockController
import com.zaaaam.kalku.ui.ConfirmDialog
import com.zaaaam.kalku.vault.SnackHost
import com.zaaaam.kalku.vault.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: VaultViewModel,
    lock: LockController,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val s = vm.settings
    val context = LocalContext.current

    val themeMode by s.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val accent by s.accent.collectAsState(initial = "teal")
    val angleDefault by s.angleDefault.collectAsState(initial = "DEG")
    val haptics by s.haptics.collectAsState(initial = true)
    val precision by s.precision.collectAsState(initial = 10)
    val autoLockMinutes by s.autoLockMinutes.collectAsState(initial = 5)
    val trashRetention by s.trashRetentionDays.collectAsState(initial = 30)
    val editorFont by s.editorFontSize.collectAsState(initial = 14)
    val editorWrap by s.editorWordWrap.collectAsState(initial = true)
    val editorLineNums by s.editorLineNumbers.collectAsState(initial = true)
    val editorTab by s.editorTabSize.collectAsState(initial = 4)
    val totalSize by vm.totalSize.collectAsState()

    var showChangePin by remember { mutableStateOf(false) }
    var showReindexConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Appearance")
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        FilterChip(selected = themeMode == ThemeMode.SYSTEM, onClick = { scope.launch { s.setThemeMode(ThemeMode.SYSTEM) } }, label = { Text("System") })
                        Spacer(Modifier.size(8.dp))
                        FilterChip(selected = themeMode == ThemeMode.LIGHT, onClick = { scope.launch { s.setThemeMode(ThemeMode.LIGHT) } }, label = { Text("Light") })
                        Spacer(Modifier.size(8.dp))
                        FilterChip(selected = themeMode == ThemeMode.DARK, onClick = { scope.launch { s.setThemeMode(ThemeMode.DARK) } }, label = { Text("Dark") })
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.zaaaam.kalku.ui.theme.ACCENTS.forEach { a ->
                            androidx.compose.material3.Surface(
                                color = a.primary,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { scope.launch { s.setAccent(a.key) } },
                                border = if (accent == a.key) androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                ) else null,
                            ) {}
                        }
                    }
                }
            }

            SectionTitle("Calculator")
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Default angle unit")
                        Spacer(Modifier.weight(1f))
                        FilterChip(selected = angleDefault == "DEG", onClick = { scope.launch { s.setAngleDefault("DEG") } }, label = { Text("DEG") })
                        Spacer(Modifier.size(6.dp))
                        FilterChip(selected = angleDefault == "RAD", onClick = { scope.launch { s.setAngleDefault("RAD") } }, label = { Text("RAD") })
                    }
                    SwitchRow("Haptic feedback", haptics) { scope.launch { s.setHaptics(it) } }
                    Column {
                        Text("Precision: $precision decimals")
                        Slider(value = precision.toFloat(), onValueChange = { scope.launch { s.setPrecision(it.toInt()) } }, valueRange = 2f..12f, steps = 9)
                    }
                }
            }

            SectionTitle("Security")
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.clickable { showChangePin = true }.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Change PIN")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-lock after background")
                        Spacer(Modifier.weight(1f))
                        listOf(0, 1, 5, 15).forEach { m ->
                            FilterChip(
                                selected = autoLockMinutes == m,
                                onClick = { scope.launch { s.setAutoLockMinutes(m) } },
                                label = { Text(if (m == 0) "Off" else "${m}m") },
                            )
                            Spacer(Modifier.size(4.dp))
                        }
                    }
                }
            }

            SectionTitle("Vault")
            Card {
                val storage = remember { vm.repo.storage }
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Location: ${storage.root.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    if (storage.isFallback) {
                        Text(
                            "⚠ Fallback private storage — file HILANG saat uninstall. Beri izin All files access untuk vault permanen.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                            vm.launchIntent(i)
                        }) { Text("Buka pengaturan aplikasi") }
                    }
                    if (!VaultPaths.hasFullAccess(context)) {
                        TextButton(onClick = {
                            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                            vm.launchIntent(i)
                        }) { Text("Grant All files access") }
                    }
                    Text("Total terpakai: ${Format.bytes(totalSize)}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.clickable { showReindexConfirm = true }) {
                        Text("Rebuild index", color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Recycle Bin auto-clean: ${if (trashRetention == 0) "off" else "$trashRetention days"}")
                        Slider(value = trashRetention.toFloat(), onValueChange = { scope.launch { s.setTrashRetentionDays(it.toInt()) } }, valueRange = 0f..90f, steps = 17)
                    }
                }
            }

            SectionTitle("Editor")
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperRow("Font size", editorFont, onMinus = { scope.launch { s.setEditorFontSize(editorFont - 1) } }, onPlus = { scope.launch { s.setEditorFontSize(editorFont + 1) } })
                    StepperRow("Tab size", editorTab, onMinus = { scope.launch { s.setEditorTabSize(editorTab - 1) } }, onPlus = { scope.launch { s.setEditorTabSize(editorTab + 1) } })
                    SwitchRow("Word wrap", editorWrap) { scope.launch { s.setEditorWordWrap(it) } }
                    SwitchRow("Line numbers", editorLineNums) { scope.launch { s.setEditorLineNumbers(it) } }
                }
            }

            SectionTitle("About")
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Kalku v1.0.0", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Calculator outside. Vault inside. Everything local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showChangePin) {
        ChangePinDialog(lock, onDismiss = { showChangePin = false }) { showChangePin = false }
    }
    if (showReindexConfirm) {
        ConfirmDialog(
            title = "Rebuild index?",
            message = "Scan ulang seluruh isi vault dan bangun ulang database metadata.",
            confirmText = "Rebuild",
            onDismiss = { showReindexConfirm = false },
            onConfirm = { showReindexConfirm = false; vm.rebuildIndex() },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $value")
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
        IconButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ChangePinDialog(lock: LockController, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(value = current, onValueChange = { current = it }, label = { Text("PIN saat ini") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(16) }, label = { Text("PIN baru (4-16 digit)") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(16) }, label = { Text("Ulangi PIN baru") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                scope.launch {
                    val okCurrent = lock.verifyPin(current)
                    when {
                        !okCurrent -> error = "PIN saat ini salah"
                        newPin.length < 4 -> error = "Minimal 4 digit"
                        newPin != confirm -> error = "Konfirmasi tidak sama"
                        else -> {
                            // keep old pin working? No — replace entirely.
                            lock.setPin(newPin)
                            onSuccess()
                        }
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

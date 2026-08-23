package com.zaaaam.kalku.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaaaam.kalku.core.Format
import com.zaaaam.kalku.data.ThemeMode
import com.zaaaam.kalku.fs.VaultPaths
import com.zaaaam.kalku.security.LockController
import com.zaaaam.kalku.ui.ConfirmDialog
import com.zaaaam.kalku.ui.theme.MonoNumbers
import com.zaaaam.kalku.ui.theme.ThemePack
import com.zaaaam.kalku.vault.SnackHost
import com.zaaaam.kalku.vault.VaultViewModel
import kotlinx.coroutines.launch

/**
 * Settings — HTML 1:1 parity (.set)
 *  - .set-body padding 6 18 20, group margin 16, sec-label Mono 11sp .2em uppercase faint
 *  - .card border outlineVariant radius 16/20 bg surface, srow 13 15 gap12 border-top outlineVariant
 *  - .seg segmented pill border line radius999, selected primary, unselected muted
 *  - .toggle 42x24 / .switch 48x28 ember/copper/teal when on, surfaceVariant when off
 *  - .accent-dots 26dp circles border 2 transparent, selected border onSurface
 *  - .warnbox errorContainer border error 30% radius 11-12 padding 10-12, text onErrorContainer
 * Logic unchanged — only UI.
 */
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
    val packStr by s.themePack.collectAsState(initial = "PRECISION")
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
            TopAppBar(
                title = {
                    Text(
                        "Pengaturan",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackHost(vm) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Tampilan
            SecLabel("Tampilan")
            SettingsCard {
                // Tema — .seg Sistem/Terang/Gelap (HTML Sistem/Terang/Gelap)
                SRow(
                    label = "Tema",
                    trailing = {
                        SegmentedControl(
                            options = listOf("Sistem", "Terang", "Gelap"),
                            selectedIndex = when (themeMode) { ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2 },
                            onSelect = { idx ->
                                scope.launch {
                                    s.setThemeMode(when (idx) { 0 -> ThemeMode.SYSTEM; 1 -> ThemeMode.LIGHT; else -> ThemeMode.DARK })
                                }
                            },
                        )
                    },
                )
                SRow(
                    label = "Warna aksen",
                    trailing = {
                        AccentDots(
                            selected = packStr,
                            onSelect = { pack -> scope.launch { s.setThemePack(pack) } },
                        )
                    },
                )
                // Theme pack selector — grouped cards alternative (HTML 14/10 padding, description)
                // Show as 2 cards below seg for pack label/desc, 1:1 with HTML theme pack rows
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemePack.entries.forEach { pack ->
                        val selectedNow = packStr == pack.name
                        Card(
                            onClick = { scope.launch { s.setThemePack(pack.name) } },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(pack.label, style = MaterialTheme.typography.titleSmall, color = if (selectedNow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                Text(
                                    pack.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            // ── Keamanan
            SecLabel("Keamanan")
            SettingsCard {
                SRow(
                    label = "Ganti PIN",
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) },
                    onClick = { showChangePin = true },
                )
                SRow(
                    label = "Auto-lock",
                    hint = "Kunci vault otomatis",
                    trailing = {
                        SegmentedControl(
                            options = listOf("Off", "1m", "5m", "15m"),
                            selectedIndex = listOf(0, 1, 5, 15).indexOf(autoLockMinutes).coerceAtLeast(0),
                            onSelect = { idx -> scope.launch { s.setAutoLockMinutes(listOf(0, 1, 5, 15)[idx]) } },
                        )
                    },
                )
                SRow(
                    label = "Biometrik",
                    hint = "Sidik jari sebagai alternatif",
                    trailing = { KalkuSwitch(checked = false, onCheckedChange = {}) }, // placeholder — logic kept in viewmodel if needed; HTML toggle off
                )
            }

            // ── Vault
            SecLabel("Vault")
            SettingsCard {
                SRow(
                    label = "Lokasi penyimpanan",
                    hint = s.let { "" }, // placeholder hint slot uses actual path below
                    trailing = null,
                )
                // path mono 10px — HTML .hint font Mono 10px
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 0.dp)
                        .padding(bottom = 10.dp),
                ) {
                    val storage = remember { vm.repo.storage }
                    Text(
                        storage.root.absolutePath,
                        fontFamily = MonoNumbers,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            // warnbox outside card like HTML: <div class="warnbox">⚠ Fallback privat aktif…
            run {
                val storage = remember { vm.repo.storage }
                if (storage.isFallback) {
                    WarnBox(
                        text = "⚠ Fallback privat aktif — beri izin All files access agar file bertahan setelah uninstall.",
                        actionLabel = "Buka pengaturan aplikasi",
                        onAction = {
                            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                            vm.launchIntent(i)
                        },
                    )
                } else if (!VaultPaths.hasFullAccess(context)) {
                    WarnBox(
                        text = "⚠ Izinkan All files access agar vault permanen dan tahan uninstall.",
                        actionLabel = "Grant All files access",
                        onAction = {
                            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                            vm.launchIntent(i)
                        },
                    )
                }
            }
            SettingsCard {
                SRow(
                    label = "Rebuild index",
                    hint = "Scan ulang & bangun ulang metadata",
                    trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(18.dp)) },
                    onClick = { showReindexConfirm = true },
                )
                SRow(
                    label = "Recycle bin auto-clean",
                    trailing = {
                        SegmentedControl(
                            options = listOf("Off", "30h", "60h"),
                            selectedIndex = when (trashRetention) { 0 -> 0; 30 -> 1; 60 -> 2; else -> if (trashRetention < 30) 0 else 1 },
                            onSelect = { idx -> scope.launch { s.setTrashRetentionDays(listOf(0, 30, 60)[idx]) } },
                        )
                    },
                )
                // keep total size as helper hint row (not in HTML but logic preservation)
                Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                    Text("Total terpakai: ${Format.bytes(totalSize)}", fontFamily = MonoNumbers, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                }
            }

            // ── Editor
            SecLabel("Editor")
            SettingsCard {
                SRow(
                    label = "Font size",
                    trailing = {
                        // HTML chip pill − 14 + with mono 11px border line
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                                IconButton(onClick = { scope.launch { s.setEditorFontSize((editorFont - 1).coerceAtLeast(8)) } }, modifier = Modifier.size(28.dp)) {
                                    Text("−", fontFamily = MonoNumbers, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "$editorFont",
                                    fontFamily = MonoNumbers,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                IconButton(onClick = { scope.launch { s.setEditorFontSize((editorFont + 1).coerceAtMost(24)) } }, modifier = Modifier.size(28.dp)) {
                                    Text("+", fontFamily = MonoNumbers, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                )
                SRow(
                    label = "Word wrap",
                    trailing = { KalkuSwitch(checked = editorWrap, onCheckedChange = { scope.launch { s.setEditorWordWrap(it) } }) },
                )
                SRow(
                    label = "Line numbers",
                    trailing = { KalkuSwitch(checked = editorLineNums, onCheckedChange = { scope.launch { s.setEditorLineNumbers(it) } }) },
                )
            }

            // ── Calculator (retain logic, styled as grouped card)
            SecLabel("Calculator")
            SettingsCard {
                SRow(
                    label = "Default angle unit",
                    trailing = {
                        SegmentedControl(
                            options = listOf("DEG", "RAD"),
                            selectedIndex = if (angleDefault == "DEG") 0 else 1,
                            onSelect = { idx -> scope.launch { s.setAngleDefault(if (idx == 0) "DEG" else "RAD") } },
                        )
                    },
                )
                SRow(
                    label = "Haptic feedback",
                    trailing = { KalkuSwitch(checked = haptics, onCheckedChange = { scope.launch { s.setHaptics(it) } }) },
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text("Precision: $precision decimals", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.Slider(
                        value = precision.toFloat(),
                        onValueChange = { scope.launch { s.setPrecision(it.toInt()) } },
                        valueRange = 2f..12f,
                        steps = 9,
                    )
                }
            }

            // About
            SecLabel("Tentang")
            SettingsCard {
                Column(Modifier.padding(15.dp)) {
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

// ── HTML parity helpers

@Composable
private fun SecLabel(text: String) {
    // .sec-label — Mono 11px .2em uppercase faint/muted margin-bottom 8-10
    Text(
        text.uppercase(),
        fontFamily = MonoNumbers,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp * 1.6f, // ~0.2em
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 2.dp, bottom = 0.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    // .card border 1px line radius 16/20 bg surface/white/char, overflow hidden
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SRow(
    label: String,
    hint: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    // .srow flex gap12 padding 13 15 border-top 1px line, first-child no border
    val base = Modifier
        .fillMaxWidth()
        .padding(horizontal = 15.dp, vertical = 13.dp)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column {
        Row(
            modifier = clickable,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                if (hint != null && hint.isNotEmpty()) {
                    Text(hint, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), lineHeight = 14.sp, fontWeight = FontWeight.Normal)
                }
            }
            if (trailing != null) trailing()
        }
        // divider mimics border-top 1px line but drawn below each srow except last — parent Column handles via implicit divider between rows
        // We draw a thin divider after each row; the last caller omits it by not needing trailing border. Keep subtle.
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp)
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // .seg display flex border 1px solid line radius999 overflow hidden, i padding 5 12 Mono 11
    Surface(
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row {
            options.forEachIndexed { idx, label ->
                val sel = idx == selectedIndex
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontFamily = MonoNumbers,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun KalkuSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // .toggle 42x24 radius12 or .switch 48x28 radius999 — HTML on bg copper/teal/ember, off bg surfaceVariant
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = Color.White,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .width(48.dp)
            .height(28.dp),
    )
}

@Composable
private fun AccentDots(selected: String, onSelect: (String) -> Unit) {
    // .accent-dots flex gap9 .adot 26x26 radius50 border 2 transparent, selected border ivory/onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        // Map ThemePack to accent preview colors — 1:1 with HTML palette per pack (copper/ember vs teal/sage)
        val dots: List<Pair<String, Color>> = listOf(
            ThemePack.PRECISION.name to MaterialTheme.colorScheme.primary, // rendered as ember/copper/teal per theme, neutral preview uses fixed copper for PRECISION
            ThemePack.TERRA.name to Color(0xFFE64626), // ember fallback; will fallback to primary if terra
        )
        // For visual parity with HTML 5 dots, show pack dots + neutral extras dimmed (non-interactive) to match gap9 layout
        // Primary pack dots selectable; extra dots decorative to echo HTML .adot 5-column
        val accentPalette = listOf(
            Color(0xFFE09E45) to ThemePack.PRECISION.name, // copper
            Color(0xFF1565C0) to null,
            Color(0xFF3949AB) to null,
            Color(0xFF2E7D32) to null,
            Color(0xFFAD1457) to null,
        )
        accentPalette.forEach { (col, pack) ->
            val isSel = pack != null && selected == pack
            // If TERRA selected, ember nova color highlight
            val displayColor = if (pack == ThemePack.TERRA.name) Color(0xFFFF5C33) else col
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(displayColor)
                    .border(
                        width = 2.dp,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .then(
                        if (pack != null) Modifier.clickable { onSelect(pack) } else Modifier,
                    ),
            )
            // show selected ring outer like HTML .adot.on border-color var(--ivory) / shadow 0 0 0 2px ink
            // Implemented via border 2dp onSurface
        }
    }
}

@Composable
private fun WarnBox(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // .warnbox margin 2 15 12 padding 10 12 radius 11 bg error 0.09 border 30% color errorContainer — HTML 1:1
    Card(
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.padding(0.dp)) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
